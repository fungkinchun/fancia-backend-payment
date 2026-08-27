package com.fancia.backend.payment.core.support.stripe

import com.fancia.backend.payment.config.ApplicationProperties
import com.fancia.backend.shared.payment.core.exception.ConnectCheckoutAmountMustBePositiveException
import com.fancia.backend.shared.payment.core.exception.ConnectCheckoutAmountTooSmallException
import com.fancia.backend.shared.payment.core.exception.ConnectCheckoutException
import com.fancia.backend.shared.payment.core.exception.ConnectCheckoutInvalidApplicationFeeException
import com.fancia.backend.shared.payment.core.exception.ConnectCheckoutProviderFailedException
import com.fancia.backend.shared.payment.core.exception.ConnectCheckoutRefundFailedException
import com.fancia.backend.shared.payment.core.exception.ConnectCheckoutRefundMissingPaymentIntentException
import com.fancia.backend.shared.payment.core.exception.ConnectCheckoutSessionUrlMissingException
import com.fancia.backend.shared.payment.core.exception.SubscriptionBillingException
import com.fancia.backend.shared.payment.core.util.StripeMinAmounts
import com.fancia.backend.shared.user.core.enums.PaymentProvider
import com.fancia.backend.shared.user.core.enums.SubscriptionStatus
import com.fancia.backend.shared.user.core.exception.InvalidStripeNotificationException
import com.stripe.Stripe
import com.stripe.exception.SignatureVerificationException
import com.stripe.model.Event
import com.stripe.model.Invoice
import com.stripe.model.Subscription
import com.stripe.model.billingportal.Session as PortalSession
import com.stripe.model.checkout.Session
import com.stripe.net.ApiResource
import com.stripe.net.RequestOptions
import com.stripe.net.Webhook
import com.stripe.param.InvoiceListParams
import com.stripe.param.SubscriptionUpdateParams
import com.stripe.param.billingportal.SessionCreateParams as PortalSessionCreateParams
import com.stripe.param.checkout.SessionCreateParams
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

data class StripeSubscriptionSnapshot(
    val subscriptionId: String,
    val productId: String?,
    val stripeStatus: String?,
    val status: SubscriptionStatus,
    val expiresAt: LocalDateTime?,
    val userIdMetadata: String?,
    val customerId: String?,
    val environment: String,
    val rawJson: String?,
    val cancelAtPeriodEnd: Boolean = false,
)

@Component
class StripeClient(
    private val applicationProperties: ApplicationProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun constructEvent(payload: String, signatureHeader: String?): Event {
        if (!applicationProperties.stripe.verifySignature) {
            log.warn("Stripe signature verification is DISABLED (app.stripe.verify-signature=false)")
            return ApiResource.GSON.fromJson(payload, Event::class.java)
                ?: throw InvalidStripeNotificationException(message = "Invalid Stripe event JSON")
        }

        val secret = applicationProperties.stripe.webhookSecret
            ?.takeIf { it.isNotBlank() }
            ?: throw InvalidStripeNotificationException(message = "app.stripe.webhook-secret is not configured")

        val sig = signatureHeader
            ?.takeIf { it.isNotBlank() }
            ?: throw InvalidStripeNotificationException(message = "Missing Stripe-Signature header")

        return try {
            Webhook.constructEvent(payload, sig, secret)
        } catch (ex: SignatureVerificationException) {
            throw InvalidStripeNotificationException(message = "Stripe signature verification failed: ${ex.message}")
        } catch (ex: Exception) {
            throw InvalidStripeNotificationException(message = "Invalid Stripe webhook payload: ${ex.message}")
        }
    }

    fun getSubscription(subscriptionId: String): StripeSubscriptionSnapshot {
        val sub = try {
            Subscription.retrieve(subscriptionId, requestOptions())
        } catch (ex: Exception) {
            log.warn("Stripe Subscription.retrieve failed id={}", subscriptionId, ex)
            throw InvalidStripeNotificationException(
                message = "Stripe subscription lookup failed: ${ex.message}",
            )
        }
        return toSnapshot(sub)
    }

    fun toSnapshot(subscription: Subscription): StripeSubscriptionSnapshot {
        val item = subscription.items?.data?.firstOrNull()
        val price = item?.price
        val productId = price?.product ?: price?.id
        val periodEndEpoch = item?.currentPeriodEnd
            ?: subscription.cancelAt
            ?: subscription.endedAt
        val expiresAt = periodEndEpoch
            ?.takeIf { it > 0 }
            ?.let { LocalDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneOffset.UTC) }
        val metadataUserId = subscription.metadata?.get("userId")
            ?: subscription.metadata?.get("user_id")
        val environment = if (applicationProperties.stripe.secretKey?.startsWith("sk_live") == true) {
            "Production"
        } else {
            "Sandbox"
        }
        val cancelAtPeriodEnd = subscription.cancelAtPeriodEnd == true

        return StripeSubscriptionSnapshot(
            subscriptionId = subscription.id,
            productId = productId,
            stripeStatus = subscription.status,
            status = mapStatus(subscription.status, expiresAt, cancelAtPeriodEnd),
            expiresAt = expiresAt,
            userIdMetadata = metadataUserId,
            customerId = subscription.customer,
            environment = environment,
            rawJson = subscription.toJson(),
            cancelAtPeriodEnd = cancelAtPeriodEnd,
        )
    }

    fun mapStatus(
        stripeStatus: String?,
        expiresAt: LocalDateTime?,
        cancelAtPeriodEnd: Boolean = false,
    ): SubscriptionStatus =
        when (stripeStatus) {
            "active", "trialing" -> {
                if (cancelAtPeriodEnd) {
                    SubscriptionStatus.CANCELLED
                } else {
                    SubscriptionStatus.ACTIVE
                }
            }
            "past_due" -> SubscriptionStatus.BILLING_RETRY
            "unpaid" -> SubscriptionStatus.BILLING_RETRY
            "canceled" -> {
                if (expiresAt != null && expiresAt.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
                    SubscriptionStatus.CANCELLED
                } else {
                    SubscriptionStatus.EXPIRED
                }
            }
            "incomplete", "incomplete_expired" -> SubscriptionStatus.EXPIRED
            "paused" -> SubscriptionStatus.CANCELLED
            else -> {
                log.info("Unknown Stripe subscription status={}", stripeStatus)
                if (expiresAt != null && expiresAt.isAfter(LocalDateTime.now(ZoneOffset.UTC))) {
                    SubscriptionStatus.ACTIVE
                } else {
                    SubscriptionStatus.EXPIRED
                }
            }
        }

    fun cancelSubscriptionAtPeriodEnd(subscriptionId: String): StripeSubscriptionSnapshot {
        val provider = PaymentProvider.STRIPE
        return try {
            val existing = Subscription.retrieve(subscriptionId, requestOptions())
            val updated = existing.update(
                SubscriptionUpdateParams.builder()
                    .setCancelAtPeriodEnd(true)
                    .build(),
                requestOptions(),
            )
            toSnapshot(updated)
        } catch (ex: SubscriptionBillingException) {
            throw ex
        } catch (ex: Exception) {
            log.warn("Stripe Subscription cancel-at-period-end failed id={}", subscriptionId, ex)
            throw SubscriptionBillingException(
                provider = provider,
                message = "Cancel failed for provider: $provider: ${ex.message}",
            )
        }
    }

    fun createCheckoutSession(
        userId: UUID,
        successUrl: String,
        cancelUrl: String,
    ): Pair<String, String> {
        val provider = PaymentProvider.STRIPE
        val priceId = applicationProperties.stripe.priceId
            ?.takeIf { it.isNotBlank() }
            ?: throw SubscriptionBillingException(
                provider = provider,
                message = "Price id is not configured for provider: $provider",
            )

        val userIdStr = userId.toString()
        val trialDays = applicationProperties.stripe.trialPeriodDays
        val subscriptionData = SessionCreateParams.SubscriptionData.builder()
            .putMetadata("userId", userIdStr)
        if (trialDays > 0) {
            subscriptionData.setTrialPeriodDays(trialDays)
        }

        val params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setSuccessUrl(successUrl)
            .setCancelUrl(cancelUrl)
            .setClientReferenceId(userIdStr)
            .putMetadata("userId", userIdStr)
            .setSubscriptionData(subscriptionData.build())
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setPrice(priceId)
                    .setQuantity(1L)
                    .build(),
            )
            .build()

        return try {
            val session = Session.create(params, requestOptions())
            val url = session.url
                ?: throw SubscriptionBillingException(
                    provider = provider,
                    message = "Checkout session missing url for provider: $provider",
                )
            url to session.id
        } catch (ex: SubscriptionBillingException) {
            throw ex
        } catch (ex: Exception) {
            log.warn("Stripe Checkout Session.create failed userId={}", userId, ex)
            throw SubscriptionBillingException(
                provider = provider,
                message = "Checkout failed for provider: $provider: ${ex.message}",
            )
        }
    }

    fun createBillingPortalSession(customerId: String, returnUrl: String): String {
        val provider = PaymentProvider.STRIPE
        val params = PortalSessionCreateParams.builder()
            .setCustomer(customerId)
            .setReturnUrl(returnUrl)
            .build()

        return try {
            val session = PortalSession.create(params, requestOptions())
            session.url
                ?: throw SubscriptionBillingException(
                    provider = provider,
                    message = "Billing portal session missing url for provider: $provider",
                )
        } catch (ex: SubscriptionBillingException) {
            throw ex
        } catch (ex: Exception) {
            log.warn("Stripe Billing Portal Session.create failed customerId={}", customerId, ex)
            throw SubscriptionBillingException(
                provider = provider,
                message = "Billing portal failed for provider: $provider: ${ex.message}",
            )
        }
    }

    fun listInvoices(customerId: String, limit: Long = 24): List<Invoice> {
        val params = InvoiceListParams.builder()
            .setCustomer(customerId)
            .setLimit(limit)
            .build()
        return try {
            Invoice.list(params, requestOptions()).data.orEmpty()
        } catch (ex: Exception) {
            log.warn("Stripe Invoice.list failed customerId={}", customerId, ex)
            throw SubscriptionBillingException(
                provider = PaymentProvider.STRIPE,
                message = "Invoice list failed for provider: ${PaymentProvider.STRIPE}: ${ex.message}",
            )
        }
    }

    fun createConnectPaymentCheckoutSession(
        buyerUserId: UUID,
        successUrl: String,
        cancelUrl: String,
        amountMinor: Long,
        currency: String,
        applicationFeeMinor: Long,
        destinationAccountId: String,
        productName: String,
        metadata: Map<String, String>,
    ): Pair<String, String> {
        val provider = PaymentProvider.STRIPE
        if (amountMinor <= 0L) {
            log.error(
                "Stripe Connect Checkout rejected: amount must be > 0 amountMinor={} currency={}",
                amountMinor,
                currency,
            )
            throw ConnectCheckoutAmountMustBePositiveException()
        }
        if (!StripeMinAmounts.meetsCheckoutMinimum(amountMinor, currency)) {
            log.error(
                "Stripe Connect Checkout rejected: amount below Stripe minimum amountMinor={} " +
                    "currency={} minimum={}",
                amountMinor,
                currency,
                StripeMinAmounts.minMinor(currency),
            )
            throw ConnectCheckoutAmountTooSmallException(
                message = "Checkout amount must be at least ${StripeMinAmounts.formatMinimum(currency)} " +
                    "(Stripe card payment minimum)",
            )
        }
        if (applicationFeeMinor < 0L || applicationFeeMinor >= amountMinor) {
            log.error(
                "Stripe Connect Checkout rejected: invalid application fee amountMinor={} " +
                    "applicationFeeMinor={} currency={}",
                amountMinor,
                applicationFeeMinor,
                currency,
            )
            throw ConnectCheckoutInvalidApplicationFeeException()
        }

        val lineItem = SessionCreateParams.LineItem.builder()
            .setQuantity(1L)
            .setPriceData(
                SessionCreateParams.LineItem.PriceData.builder()
                    .setCurrency(currency.lowercase())
                    .setUnitAmount(amountMinor)
                    .setProductData(
                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                            .setName(productName)
                            .build(),
                    )
                    .build(),
            )
            .build()

        val paymentIntentData = SessionCreateParams.PaymentIntentData.builder()
            .setApplicationFeeAmount(applicationFeeMinor)
            .setTransferData(
                SessionCreateParams.PaymentIntentData.TransferData.builder()
                    .setDestination(destinationAccountId)
                    .build(),
            )
        metadata.forEach { (k, v) -> paymentIntentData.putMetadata(k, v) }
        paymentIntentData.putMetadata("userId", buyerUserId.toString())

        val paramsBuilder = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.PAYMENT)
            .setSuccessUrl(successUrl)
            .setCancelUrl(cancelUrl)
            .setClientReferenceId(buyerUserId.toString())
            .addLineItem(lineItem)
            .setPaymentIntentData(paymentIntentData.build())
            .setManagedPayments(
                SessionCreateParams.ManagedPayments.builder()
                    .setEnabled(false)
                    .build(),
            )
        metadata.forEach { (k, v) -> paramsBuilder.putMetadata(k, v) }
        paramsBuilder.putMetadata("userId", buyerUserId.toString())

        return try {
            val session = Session.create(paramsBuilder.build(), requestOptions())
            val url = session.url
                ?: throw ConnectCheckoutSessionUrlMissingException(
                    message = "Checkout session missing url for provider: $provider",
                )
            url to session.id
        } catch (ex: ConnectCheckoutException) {
            throw ex
        } catch (ex: Exception) {
            log.warn(
                "Stripe Connect Checkout Session.create failed buyer={} destination={}",
                buyerUserId,
                destinationAccountId,
                ex,
            )
            throw ConnectCheckoutProviderFailedException(
                message = "Checkout failed for provider: $provider: ${ex.message}",
            )
        }
    }

    fun refundConnectCheckoutSession(checkoutSessionId: String) {
        try {
            val session = Session.retrieve(checkoutSessionId, requestOptions())
            val paymentIntentId = session.paymentIntent
                ?: throw ConnectCheckoutRefundMissingPaymentIntentException()
            com.stripe.model.Refund.create(
                com.stripe.param.RefundCreateParams.builder()
                    .setPaymentIntent(paymentIntentId)
                    .setReverseTransfer(true)
                    .setRefundApplicationFee(true)
                    .build(),
                requestOptions(),
            )
        } catch (ex: ConnectCheckoutException) {
            throw ex
        } catch (ex: Exception) {
            log.warn("Stripe refund failed session={}", checkoutSessionId, ex)
            throw ConnectCheckoutRefundFailedException(message = "Refund failed: ${ex.message}")
        }
    }

    private fun requestOptions(): RequestOptions {
        val key = applicationProperties.stripe.secretKey
            ?.takeIf { it.isNotBlank() }
            ?: throw InvalidStripeNotificationException(message = "app.stripe.secret-key is not configured")

        Stripe.apiKey = key
        return RequestOptions.builder().setApiKey(key).build()
    }
}
