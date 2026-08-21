package com.fancia.backend.payment.core.support.google

import com.fancia.backend.payment.config.ApplicationProperties
import com.fancia.backend.shared.user.core.enums.SubscriptionStatus
import com.fancia.backend.shared.user.core.exception.InvalidGoogleNotificationException
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.androidpublisher.AndroidPublisher
import com.google.api.services.androidpublisher.AndroidPublisherScopes
import com.google.api.services.androidpublisher.model.SubscriptionPurchaseV2
import com.google.auth.http.HttpCredentialsAdapter
import com.google.auth.oauth2.GoogleCredentials
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Base64
import java.util.concurrent.atomic.AtomicReference

data class GoogleSubscriptionSnapshot(
    val purchaseToken: String,
    val productId: String?,
    val subscriptionState: String?,
    val status: SubscriptionStatus,
    val expiresAt: LocalDateTime?,
    val obfuscatedExternalAccountId: String?,
    val environment: String,
    val rawJson: String?,
)

@Component
class GooglePlayDeveloperClient(
    private val applicationProperties: ApplicationProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val publisherRef = AtomicReference<AndroidPublisher?>()

    fun getSubscription(purchaseToken: String, packageName: String? = null): GoogleSubscriptionSnapshot {
        val pkg = packageName?.takeIf { it.isNotBlank() }
            ?: applicationProperties.google.packageName?.takeIf { it.isNotBlank() }
            ?: throw InvalidGoogleNotificationException(message = "app.google.package-name is not configured")

        val purchase = try {
            androidPublisher()
                .purchases()
                .subscriptionsv2()
                .get(pkg, purchaseToken)
                .execute()
        } catch (ex: Exception) {
            log.warn("subscriptionsv2.get failed package={} tokenPrefix={}", pkg, purchaseToken.take(12), ex)
            throw InvalidGoogleNotificationException(
                message = "Google Play subscription lookup failed: ${ex.message}",
            )
        }

        return toSnapshot(purchaseToken, purchase)
    }

    private fun toSnapshot(purchaseToken: String, purchase: SubscriptionPurchaseV2): GoogleSubscriptionSnapshot {
        val state = purchase.subscriptionState
        val lineItem = purchase.lineItems?.firstOrNull()
        val expiry = lineItem?.expiryTime?.let { parseGoogleTime(it) }
        val productId = lineItem?.productId
        val obfuscated = purchase.externalAccountIdentifiers?.obfuscatedExternalAccountId
        val environment = if (purchase.testPurchase != null) "Sandbox" else "Production"
        val status = mapState(state, expiry)

        return GoogleSubscriptionSnapshot(
            purchaseToken = purchaseToken,
            productId = productId,
            subscriptionState = state,
            status = status,
            expiresAt = expiry,
            obfuscatedExternalAccountId = obfuscated,
            environment = environment,
            rawJson = purchase.toPrettyString(),
        )
    }

    fun mapState(subscriptionState: String?, expiresAt: LocalDateTime?): SubscriptionStatus {
        return when (subscriptionState) {
            "SUBSCRIPTION_STATE_ACTIVE",
            "SUBSCRIPTION_STATE_CANCELED",
            -> {

                if (expiresAt != null && expiresAt.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
                    SubscriptionStatus.ACTIVE
                } else if (subscriptionState == "SUBSCRIPTION_STATE_CANCELED") {
                    SubscriptionStatus.CANCELLED
                } else {
                    SubscriptionStatus.ACTIVE
                }
            }
            "SUBSCRIPTION_STATE_IN_GRACE_PERIOD" -> SubscriptionStatus.GRACE_PERIOD
            "SUBSCRIPTION_STATE_ON_HOLD" -> SubscriptionStatus.BILLING_RETRY
            "SUBSCRIPTION_STATE_PAUSED" -> SubscriptionStatus.CANCELLED
            "SUBSCRIPTION_STATE_EXPIRED",
            "SUBSCRIPTION_STATE_PENDING_PURCHASE_CANCELED",
            -> SubscriptionStatus.EXPIRED
            "SUBSCRIPTION_STATE_PENDING" -> SubscriptionStatus.BILLING_RETRY
            else -> {
                log.info("Unknown Google subscriptionState={}", subscriptionState)
                if (expiresAt != null && expiresAt.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
                    SubscriptionStatus.ACTIVE
                } else {
                    SubscriptionStatus.EXPIRED
                }
            }
        }
    }

    private fun parseGoogleTime(value: String): LocalDateTime? =
        runCatching {
            LocalDateTime.ofInstant(Instant.parse(value), ZoneOffset.UTC)
        }.getOrNull()

    private fun androidPublisher(): AndroidPublisher {
        publisherRef.get()?.let { return it }
        synchronized(this) {
            publisherRef.get()?.let { return it }
            val credentials = loadCredentials()
                .createScoped(listOf(AndroidPublisherScopes.ANDROIDPUBLISHER))
            val transport = GoogleNetHttpTransport.newTrustedTransport()
            val jsonFactory = GsonFactory.getDefaultInstance()
            val publisher = AndroidPublisher.Builder(transport, jsonFactory, HttpCredentialsAdapter(credentials))
                .setApplicationName(applicationProperties.applicationName ?: "fancia-payment")
                .build()
            publisherRef.set(publisher)
            return publisher
        }
    }

    private fun loadCredentials(): GoogleCredentials {
        val raw = applicationProperties.google.serviceAccountJson
            ?.takeIf { it.isNotBlank() }
            ?: throw InvalidGoogleNotificationException(
                message = "app.google.service-account-json is not configured",
            )

        val bytes = decodeServiceAccountJson(raw)
        return GoogleCredentials.fromStream(ByteArrayInputStream(bytes))
    }

    private fun decodeServiceAccountJson(raw: String): ByteArray {
        val trimmed = raw.trim()
        if (trimmed.startsWith("{")) {
            return trimmed.toByteArray()
        }
        return try {
            Base64.getDecoder().decode(trimmed)
        } catch (_: IllegalArgumentException) {
            trimmed.toByteArray()
        }
    }
}
