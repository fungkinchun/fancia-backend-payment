package com.fancia.backend.payment.core.support.apple

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset

data class AppleNotification(
    val notificationType: String,
    val subtype: String?,
    val notificationUUID: String,
    val environment: String?,
    val bundleId: String?,
    val signedDate: Long?,
    val transaction: AppleTransactionInfo?,
    val renewal: AppleRenewalInfo?,
    val rawPayload: String,
)

data class AppleTransactionInfo(
    val originalTransactionId: String,
    val transactionId: String?,
    val productId: String?,
    val expiresDateMs: Long?,
    val appAccountToken: String?,
    val type: String?,
    val environment: String?,
) {
    fun expiresAt(): LocalDateTime? =
        expiresDateMs?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC)
        }
}

data class AppleRenewalInfo(
    val originalTransactionId: String?,
    val autoRenewStatus: Int?,
    val autoRenewProductId: String?,
    val expirationIntent: Int?,
    val gracePeriodExpiresDateMs: Long?,
) {
    fun gracePeriodExpiresAt(): LocalDateTime? =
        gracePeriodExpiresDateMs?.let {
            LocalDateTime.ofInstant(Instant.ofEpochMilli(it), ZoneOffset.UTC)
        }
}

object AppleNotificationParser {
    private val mapper: ObjectMapper = jacksonObjectMapper()

    fun parseNotificationPayload(json: String): AppleNotification {
        val root = mapper.readTree(json)
        val data = root.path("data")
        val signedTransactionInfo = data.path("signedTransactionInfo").asText(null)
        val signedRenewalInfo = data.path("signedRenewalInfo").asText(null)

        return AppleNotification(
            notificationType = root.path("notificationType").asText(),
            subtype = root.path("subtype").takeIf { !it.isMissingNode && !it.isNull }?.asText(),
            notificationUUID = root.path("notificationUUID").asText(),
            environment = data.path("environment").asText(null),
            bundleId = data.path("bundleId").asText(null),
            signedDate = root.path("signedDate").takeIf { it.isNumber }?.asLong(),
            transaction = signedTransactionInfo?.let { parseTransactionJwsPayload(decodeJwsPayload(it)) },
            renewal = signedRenewalInfo?.let { parseRenewalJwsPayload(decodeJwsPayload(it)) },
            rawPayload = json,
        )
    }

    fun decodeJwsPayload(jws: String): String {
        val parts = jws.split(".")
        require(parts.size >= 2) { "Invalid JWS compact serialization" }
        val payload = parts[1]
            .replace('-', '+')
            .replace('_', '/')
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        return String(java.util.Base64.getDecoder().decode(padded))
    }

    private fun parseTransactionJwsPayload(json: String): AppleTransactionInfo {
        val node = mapper.readTree(json)
        return AppleTransactionInfo(
            originalTransactionId = node.requiredText("originalTransactionId"),
            transactionId = node.textOrNull("transactionId"),
            productId = node.textOrNull("productId"),
            expiresDateMs = node.longOrNull("expiresDate"),
            appAccountToken = node.textOrNull("appAccountToken"),
            type = node.textOrNull("type"),
            environment = node.textOrNull("environment"),
        )
    }

    private fun parseRenewalJwsPayload(json: String): AppleRenewalInfo {
        val node = mapper.readTree(json)
        return AppleRenewalInfo(
            originalTransactionId = node.textOrNull("originalTransactionId"),
            autoRenewStatus = node.intOrNull("autoRenewStatus"),
            autoRenewProductId = node.textOrNull("autoRenewProductId"),
            expirationIntent = node.intOrNull("expirationIntent"),
            gracePeriodExpiresDateMs = node.longOrNull("gracePeriodExpiresDate"),
        )
    }

    private fun JsonNode.requiredText(field: String): String =
        path(field).asText(null) ?: error("Missing required field: $field")

    private fun JsonNode.textOrNull(field: String): String? =
        path(field).takeIf { !it.isMissingNode && !it.isNull }?.asText()

    private fun JsonNode.longOrNull(field: String): Long? =
        path(field).takeIf { it.isNumber }?.asLong()

    private fun JsonNode.intOrNull(field: String): Int? =
        path(field).takeIf { it.isNumber }?.asInt()
}
