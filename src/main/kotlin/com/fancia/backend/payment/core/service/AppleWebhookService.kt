package com.fancia.backend.payment.core.service

import com.fancia.backend.payment.core.support.apple.AppleJwsVerifier
import com.fancia.backend.payment.core.support.apple.AppleNotification
import com.fancia.backend.shared.user.core.enums.PaymentProvider
import com.fancia.backend.shared.user.core.enums.SubscriptionStatus
import com.fancia.backend.shared.user.core.exception.InvalidAppleNotificationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class AppleWebhookService(
    private val appleJwsVerifier: AppleJwsVerifier,
    private val webhookIdempotencyService: WebhookIdempotencyService,
    private val subscriptionService: SubscriptionService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun handleSignedPayload(signedPayload: String) {
        val notification = appleJwsVerifier.verifyAndDecodeNotification(signedPayload)
        val claimed = webhookIdempotencyService.tryClaim(
            provider = PaymentProvider.APPLE,
            eventId = notification.notificationUUID,
            eventType = notification.notificationType,
            rawPayload = notification.rawPayload,
        )
        if (!claimed) {
            return
        }

        applyNotification(notification)
    }

    private fun applyNotification(notification: AppleNotification) {
        val transaction = notification.transaction
            ?: throw InvalidAppleNotificationException(message = "Missing signedTransactionInfo")

        val originalTransactionId = transaction.originalTransactionId
        val userId = subscriptionService.resolveUserId(
            provider = PaymentProvider.APPLE,
            providerSubscriptionId = originalTransactionId,
            appAccountToken = transaction.appAccountToken,
        )

        val type = notification.notificationType.uppercase()
        val subtype = notification.subtype?.uppercase()
        val environment = notification.environment ?: transaction.environment
        val expiresAt = transaction.expiresAt()
            ?: notification.renewal?.gracePeriodExpiresAt()

        log.info(
            "Apple notification type={} subtype={} uuid={} originalTransactionId={} userId={} env={}",
            type,
            subtype,
            notification.notificationUUID,
            originalTransactionId,
            userId,
            environment,
        )

        when (type) {
            "SUBSCRIBED", "DID_RENEW", "OFFER_REDEEMED", "DID_CHANGE_RENEWAL_PREF" -> {
                subscriptionService.activateSubscription(
                    userId = userId,
                    provider = PaymentProvider.APPLE,
                    providerSubscriptionId = originalTransactionId,
                    productId = transaction.productId,
                    expiresAt = expiresAt,
                    status = SubscriptionStatus.ACTIVE,
                    environment = environment,
                    rawData = notification.rawPayload,
                )
            }

            "DID_CHANGE_RENEWAL_STATUS" -> {

                subscriptionService.activateSubscription(
                    userId = userId,
                    provider = PaymentProvider.APPLE,
                    providerSubscriptionId = originalTransactionId,
                    productId = transaction.productId,
                    expiresAt = expiresAt,
                    status = SubscriptionStatus.ACTIVE,
                    environment = environment,
                    rawData = notification.rawPayload,
                )
            }

            "DID_FAIL_TO_RENEW" -> {
                val status = if (subtype == "GRACE_PERIOD") {
                    SubscriptionStatus.GRACE_PERIOD
                } else {
                    SubscriptionStatus.BILLING_RETRY
                }
                subscriptionService.activateSubscription(
                    userId = userId,
                    provider = PaymentProvider.APPLE,
                    providerSubscriptionId = originalTransactionId,
                    productId = transaction.productId,
                    expiresAt = expiresAt ?: notification.renewal?.gracePeriodExpiresAt(),
                    status = status,
                    environment = environment,
                    rawData = notification.rawPayload,
                )
            }

            "GRACE_PERIOD" -> {
                subscriptionService.activateSubscription(
                    userId = userId,
                    provider = PaymentProvider.APPLE,
                    providerSubscriptionId = originalTransactionId,
                    productId = transaction.productId,
                    expiresAt = expiresAt ?: notification.renewal?.gracePeriodExpiresAt(),
                    status = SubscriptionStatus.GRACE_PERIOD,
                    environment = environment,
                    rawData = notification.rawPayload,
                )
            }

            "EXPIRED" -> {
                subscriptionService.deactivateSubscription(
                    provider = PaymentProvider.APPLE,
                    providerSubscriptionId = originalTransactionId,
                    reason = SubscriptionStatus.EXPIRED,
                    rawData = notification.rawPayload,
                )
            }

            "REFUND", "REVOKE", "REFUND_REVERSED" -> {
                val reason = if (type == "REFUND_REVERSED") {
                    SubscriptionStatus.ACTIVE
                } else {
                    SubscriptionStatus.REFUNDED
                }
                if (reason == SubscriptionStatus.ACTIVE) {
                    subscriptionService.activateSubscription(
                        userId = userId,
                        provider = PaymentProvider.APPLE,
                        providerSubscriptionId = originalTransactionId,
                        productId = transaction.productId,
                        expiresAt = expiresAt,
                        status = SubscriptionStatus.ACTIVE,
                        environment = environment,
                        rawData = notification.rawPayload,
                    )
                } else {
                    subscriptionService.deactivateSubscription(
                        provider = PaymentProvider.APPLE,
                        providerSubscriptionId = originalTransactionId,
                        reason = SubscriptionStatus.REFUNDED,
                        rawData = notification.rawPayload,
                    )
                }
            }

            else -> {
                log.info("Ignoring unhandled Apple notification type={}", type)
            }
        }
    }
}
