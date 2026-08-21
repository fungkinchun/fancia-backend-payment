package com.fancia.backend.payment.core.service

import com.fancia.backend.payment.config.ApplicationProperties
import com.fancia.backend.payment.core.support.google.GoogleNotificationParser
import com.fancia.backend.payment.core.support.google.GooglePlayDeveloperClient
import com.fancia.backend.payment.core.support.google.GooglePubSubOidcVerifier
import com.fancia.backend.payment.core.support.google.GoogleSubscriptionNotificationType
import com.fancia.backend.shared.user.core.enums.PaymentProvider
import com.fancia.backend.shared.user.core.enums.SubscriptionStatus
import com.fancia.backend.shared.user.core.exception.InvalidGoogleNotificationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class GoogleWebhookService(
    private val applicationProperties: ApplicationProperties,
    private val pubSubOidcVerifier: GooglePubSubOidcVerifier,
    private val googlePlayDeveloperClient: GooglePlayDeveloperClient,
    private val webhookIdempotencyService: WebhookIdempotencyService,
    private val subscriptionService: SubscriptionService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun handle(rawBody: String, authorizationHeader: String?) {
        pubSubOidcVerifier.verifyAuthorizationHeader(authorizationHeader)

        val push = GoogleNotificationParser.parsePubSubPush(rawBody)
        val message = push.message
            ?: throw InvalidGoogleNotificationException(message = "Missing Pub/Sub message")
        val messageId = message.resolvedMessageId()
            ?: throw InvalidGoogleNotificationException(message = "Missing Pub/Sub messageId")
        val data = message.data
            ?: throw InvalidGoogleNotificationException(message = "Missing Pub/Sub message.data")

        val notification = GoogleNotificationParser.decodeDeveloperNotification(data)

        if (notification.isTest()) {
            log.info("Google Play test notification received messageId={}", messageId)
            webhookIdempotencyService.tryClaim(
                provider = PaymentProvider.GOOGLE,
                eventId = messageId,
                eventType = "TEST",
                rawPayload = notification.rawPayload,
            )
            return
        }

        val subNotification = notification.subscriptionNotification
        if (subNotification == null) {
            log.info(
                "Ignoring non-subscription Google notification messageId={} (oneTime/voided/other)",
                messageId,
            )
            webhookIdempotencyService.tryClaim(
                provider = PaymentProvider.GOOGLE,
                eventId = messageId,
                eventType = "NON_SUBSCRIPTION",
                rawPayload = notification.rawPayload,
            )
            return
        }

        val expectedPackage = applicationProperties.google.packageName
        if (!expectedPackage.isNullOrBlank() &&
            !notification.packageName.isNullOrBlank() &&
            notification.packageName != expectedPackage
        ) {
            throw InvalidGoogleNotificationException(
                message = "Unexpected packageName: ${notification.packageName}",
            )
        }

        val typeName = GoogleSubscriptionNotificationType.nameOf(subNotification.notificationType)
        val claimed = webhookIdempotencyService.tryClaim(
            provider = PaymentProvider.GOOGLE,
            eventId = messageId,
            eventType = typeName,
            rawPayload = notification.rawPayload,
        )
        if (!claimed) {
            return
        }

        if (subNotification.notificationType == GoogleSubscriptionNotificationType.REVOKED) {
            applyRevoked(subNotification.purchaseToken, notification.rawPayload)
            return
        }

        val snapshot = googlePlayDeveloperClient.getSubscription(
            purchaseToken = subNotification.purchaseToken,
            packageName = notification.packageName,
        )

        val status = when (subNotification.notificationType) {
            GoogleSubscriptionNotificationType.EXPIRED -> SubscriptionStatus.EXPIRED
            GoogleSubscriptionNotificationType.REVOKED -> SubscriptionStatus.REFUNDED
            else -> snapshot.status
        }

        val userId = subscriptionService.resolveUserId(
            provider = PaymentProvider.GOOGLE,
            providerSubscriptionId = snapshot.purchaseToken,
            appAccountToken = snapshot.obfuscatedExternalAccountId,
        )

        log.info(
            "Google RTDN type={} messageId={} purchaseTokenPrefix={} productId={} state={} status={} userId={} env={}",
            typeName,
            messageId,
            snapshot.purchaseToken.take(12),
            snapshot.productId ?: subNotification.subscriptionId,
            snapshot.subscriptionState,
            status,
            userId,
            snapshot.environment,
        )

        when (status) {
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.GRACE_PERIOD,
            SubscriptionStatus.BILLING_RETRY,
            -> {
                subscriptionService.activateSubscription(
                    userId = userId,
                    provider = PaymentProvider.GOOGLE,
                    providerSubscriptionId = snapshot.purchaseToken,
                    productId = snapshot.productId ?: subNotification.subscriptionId,
                    expiresAt = snapshot.expiresAt,
                    status = status,
                    environment = snapshot.environment,
                    rawData = snapshot.rawJson ?: notification.rawPayload,
                )
            }
            SubscriptionStatus.EXPIRED,
            SubscriptionStatus.CANCELLED,
            SubscriptionStatus.REFUNDED,
            -> {
                subscriptionService.deactivateSubscription(
                    provider = PaymentProvider.GOOGLE,
                    providerSubscriptionId = snapshot.purchaseToken,
                    reason = status,
                    rawData = snapshot.rawJson ?: notification.rawPayload,
                )
            }
        }
    }

    private fun applyRevoked(purchaseToken: String, rawPayload: String) {
        log.info("Google subscription revoked purchaseTokenPrefix={}", purchaseToken.take(12))
        subscriptionService.deactivateSubscription(
            provider = PaymentProvider.GOOGLE,
            providerSubscriptionId = purchaseToken,
            reason = SubscriptionStatus.REFUNDED,
            rawData = rawPayload,
        )
    }
}
