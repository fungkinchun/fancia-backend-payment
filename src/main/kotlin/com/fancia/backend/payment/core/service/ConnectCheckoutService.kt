package com.fancia.backend.payment.core.service

import com.fancia.backend.payment.config.ApplicationProperties
import com.fancia.backend.payment.core.support.stripe.StripeClient
import com.fancia.backend.shared.payment.core.dto.ConnectCheckoutResponse
import com.fancia.backend.shared.payment.core.dto.CreateConnectCheckoutSessionRequest
import com.fancia.backend.shared.payment.core.exception.ConnectCheckoutException
import com.fancia.backend.shared.payment.core.message.ConnectCheckoutCompletedEvent
import com.stripe.model.checkout.Session
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.net.URI
import java.util.UUID

@Service
class ConnectCheckoutService(
    private val stripeClient: StripeClient,
    private val platformFeeCalculator: PlatformFeeCalculator,
    private val stripeConnectedAccountLookup: StripeConnectedAccountLookup,
    private val paymentTransactionService: PaymentTransactionService,
    private val connectCheckoutFulfillmentService: ConnectCheckoutFulfillmentService,
    private val applicationProperties: ApplicationProperties,
    private val kafkaTemplate: KafkaTemplate<UUID, ConnectCheckoutCompletedEvent>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun createSession(request: CreateConnectCheckoutSessionRequest): ConnectCheckoutResponse {
        validateRedirectUrl(request.successUrl, "successUrl")
        validateRedirectUrl(request.cancelUrl, "cancelUrl")
        if (request.amountMinor <= 0L) {
            throw ConnectCheckoutException(message = "Checkout amount must be greater than zero")
        }

        val destination = stripeConnectedAccountLookup.requirePayoutReadyAccountId(request.sellerUserId)
        val fee = platformFeeCalculator.applicationFeeMinor(request.sellerUserId, request.amountMinor)
        val metadata = linkedMapOf(
            "purpose" to request.purpose,
            "resourceId" to request.resourceId,
            "sellerUserId" to request.sellerUserId.toString(),
        )
        request.metadata.forEach { (k, v) -> metadata.putIfAbsent(k, v) }

        val (url, sessionId) = stripeClient.createConnectPaymentCheckoutSession(
            buyerUserId = request.buyerUserId,
            successUrl = request.successUrl,
            cancelUrl = request.cancelUrl,
            amountMinor = request.amountMinor,
            currency = request.currency,
            applicationFeeMinor = fee,
            destinationAccountId = destination,
            productName = request.productName,
            metadata = metadata,
        )

        return ConnectCheckoutResponse(
            url = url,
            sessionId = sessionId,
            amountMinor = request.amountMinor,
            applicationFeeMinor = fee,
            currency = request.currency,
        )
    }

    @Transactional
    fun handleCheckoutSessionCompleted(session: Session) {
        val purpose = session.metadata?.get("purpose")
            ?: run {
                log.info("Ignoring checkout.session.completed without purpose session={}", session.id)
                return
            }
        val resourceId = session.metadata?.get("resourceId")
            ?: run {
                log.warn("checkout.session.completed missing resourceId session={}", session.id)
                return
            }
        val buyerUserId = session.metadata?.get("userId")
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: run {
                log.warn("checkout.session.completed missing userId session={}", session.id)
                return
            }
        val sellerUserId = session.metadata?.get("sellerUserId")
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        paymentTransactionService.upsertFromConnectCheckoutSession(
            userId = buyerUserId,
            session = session,
            purpose = purpose,
        )

        val event = ConnectCheckoutCompletedEvent(
            purpose = purpose,
            resourceId = resourceId,
            buyerUserId = buyerUserId,
            sellerUserId = sellerUserId,
            checkoutSessionId = session.id,
            amountMinor = session.amountTotal ?: 0L,
            currency = session.currency?.lowercase() ?: "gbp",
            metadata = session.metadata.orEmpty(),
        )

        runCatching {
            connectCheckoutFulfillmentService.fulfill(event)
        }.onFailure {
            log.error(
                "Feign fulfillment failed purpose={} resourceId={} session={}; Kafka consumers may retry",
                purpose,
                resourceId,
                session.id,
                it,
            )
        }

        try {
            kafkaTemplate.send(CONNECT_CHECKOUTS_TOPIC, buyerUserId, event)
        } catch (ex: Exception) {
            log.error(
                "Failed to publish ConnectCheckoutCompletedEvent purpose={} resourceId={} session={}",
                purpose,
                resourceId,
                session.id,
                ex,
            )
            throw ex
        }
    }

    private fun validateRedirectUrl(url: String, field: String) {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            throw ConnectCheckoutException(message = "$field is not a valid URL")
        }
        when (uri.scheme?.lowercase()) {
            "http", "https" -> {
                val origin = "${uri.scheme}://${uri.authority}"
                val allowed = applicationProperties.allowedOrigins
                if (allowed.isNotEmpty() &&
                    allowed.none { origin.equals(it.trimEnd('/'), ignoreCase = true) }
                ) {
                    throw ConnectCheckoutException(message = "$field origin is not allowed")
                }
            }
            "fancia" -> Unit
            else -> throw ConnectCheckoutException(message = "$field must be http(s) or fancia://")
        }
    }

    companion object {
        const val CONNECT_CHECKOUTS_TOPIC = "connect-checkouts"
    }
}
