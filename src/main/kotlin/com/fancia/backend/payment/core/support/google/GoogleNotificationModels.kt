package com.fancia.backend.payment.core.support.google

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.util.Base64

data class GooglePubSubPushRequest(
    val message: GooglePubSubMessage?,
    val subscription: String?,
)

data class GooglePubSubMessage(
    val data: String?,
    val messageId: String?,
    val message_id: String?,
    val publishTime: String?,
    val publish_time: String?,
) {
    fun resolvedMessageId(): String? = messageId ?: message_id
}

data class GoogleDeveloperNotification(
    val version: String?,
    val packageName: String?,
    val eventTimeMillis: String?,
    val subscriptionNotification: GoogleSubscriptionNotification?,
    val oneTimeProductNotification: JsonNode?,
    val voidedPurchaseNotification: JsonNode?,
    val testNotification: JsonNode?,
    val rawPayload: String,
) {
    fun isTest(): Boolean = testNotification != null && !testNotification.isNull
}

data class GoogleSubscriptionNotification(
    val version: String?,
    val notificationType: Int,
    val purchaseToken: String,
    val subscriptionId: String?,
)

object GoogleNotificationParser {
    private val mapper: ObjectMapper = jacksonObjectMapper()

    fun parsePubSubPush(rawBody: String): GooglePubSubPushRequest =
        mapper.readValue(rawBody, GooglePubSubPushRequest::class.java)

    fun decodeDeveloperNotification(base64Data: String): GoogleDeveloperNotification {
        val json = String(Base64.getDecoder().decode(base64Data))
        val root = mapper.readTree(json)
        val sub = root.path("subscriptionNotification")
        return GoogleDeveloperNotification(
            version = root.textOrNull("version"),
            packageName = root.textOrNull("packageName"),
            eventTimeMillis = root.textOrNull("eventTimeMillis"),
            subscriptionNotification = sub.takeIf { !it.isMissingNode && !it.isNull }?.let {
                GoogleSubscriptionNotification(
                    version = it.textOrNull("version"),
                    notificationType = it.path("notificationType").asInt(),
                    purchaseToken = it.requiredText("purchaseToken"),
                    subscriptionId = it.textOrNull("subscriptionId"),
                )
            },
            oneTimeProductNotification = root.get("oneTimeProductNotification"),
            voidedPurchaseNotification = root.get("voidedPurchaseNotification"),
            testNotification = root.get("testNotification"),
            rawPayload = json,
        )
    }

    private fun JsonNode.requiredText(field: String): String =
        path(field).asText(null) ?: error("Missing required field: $field")

    private fun JsonNode.textOrNull(field: String): String? =
        path(field).takeIf { !it.isMissingNode && !it.isNull }?.asText()
}

object GoogleSubscriptionNotificationType {
    const val RECOVERED = 1
    const val RENEWED = 2
    const val CANCELED = 3
    const val PURCHASED = 4
    const val ON_HOLD = 5
    const val IN_GRACE_PERIOD = 6
    const val RESTARTED = 7
    const val PRICE_CHANGE_CONFIRMED = 8
    const val DEFERRED = 9
    const val PAUSED = 10
    const val PAUSE_SCHEDULE_CHANGED = 11
    const val REVOKED = 12
    const val EXPIRED = 13
    const val PENDING_PURCHASE_CANCELED = 20

    fun nameOf(type: Int): String = when (type) {
        RECOVERED -> "SUBSCRIPTION_RECOVERED"
        RENEWED -> "SUBSCRIPTION_RENEWED"
        CANCELED -> "SUBSCRIPTION_CANCELED"
        PURCHASED -> "SUBSCRIPTION_PURCHASED"
        ON_HOLD -> "SUBSCRIPTION_ON_HOLD"
        IN_GRACE_PERIOD -> "SUBSCRIPTION_IN_GRACE_PERIOD"
        RESTARTED -> "SUBSCRIPTION_RESTARTED"
        PRICE_CHANGE_CONFIRMED -> "SUBSCRIPTION_PRICE_CHANGE_CONFIRMED"
        DEFERRED -> "SUBSCRIPTION_DEFERRED"
        PAUSED -> "SUBSCRIPTION_PAUSED"
        PAUSE_SCHEDULE_CHANGED -> "SUBSCRIPTION_PAUSE_SCHEDULE_CHANGED"
        REVOKED -> "SUBSCRIPTION_REVOKED"
        EXPIRED -> "SUBSCRIPTION_EXPIRED"
        PENDING_PURCHASE_CANCELED -> "SUBSCRIPTION_PENDING_PURCHASE_CANCELED"
        else -> "UNKNOWN_$type"
    }
}
