package com.fancia.backend.payment.core.service

import com.fancia.backend.payment.core.support.stripe.StripeClient
import com.fancia.backend.payment.core.support.stripe.StripeConnectClient
import com.fancia.backend.shared.user.core.enums.PaymentProvider
import com.fancia.backend.shared.user.core.enums.SubscriptionStatus
import com.fancia.backend.shared.user.core.exception.InvalidStripeNotificationException
import com.stripe.model.Account
import com.stripe.model.Invoice
import com.stripe.model.Subscription
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class StripeWebhookService(
    private val stripeClient: StripeClient,
    private val stripeConnectClient: StripeConnectClient,
    private val webhookIdempotencyService: WebhookIdempotencyService,
    private val subscriptionService: SubscriptionService,
    private val paymentTransactionService: PaymentTransactionService,
    private val connectAccountService: ConnectAccountService,
    private val connectCheckoutService: ConnectCheckoutService,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun handle(rawBody: String, stripeSignature: String?) {
        val event = stripeClient.constructEvent(rawBody, stripeSignature)
        val claimed = webhookIdempotencyService.tryClaim(
            provider = PaymentProvider.STRIPE,
            eventId = event.id,
            eventType = event.type,
            rawPayload = rawBody,
        )
        if (!claimed) {
            return
        }

        when (event.type) {
            "customer.subscription.created",
            "customer.subscription.updated",
            "customer.subscription.deleted",
            -> handleSubscriptionEvent(event.type, deserializeSubscription(event))

            "invoice.paid",
            "invoice.payment_failed",
            "invoice.payment_action_required",
            -> handleInvoiceEvent(event.type, deserializeInvoice(event))

            "account.updated",
            -> connectAccountService.applySnapshot(
                stripeConnectClient.toSnapshot(deserializeAccount(event)),
            )

            "checkout.session.completed",
            -> connectCheckoutService.handleCheckoutSessionCompleted(deserializeCheckoutSession(event))

            else -> log.info("Ignoring unhandled Stripe event type={} id={}", event.type, event.id)
        }
    }

    private fun handleSubscriptionEvent(type: String, subscription: Subscription) {
        val snapshot = if (type == "customer.subscription.deleted") {

            runCatching { stripeClient.getSubscription(subscription.id) }
                .getOrElse { stripeClient.toSnapshot(subscription) }
                .let {
                    it.copy(
                        status = SubscriptionStatus.EXPIRED,
                        stripeStatus = "canceled",
                    )
                }
        } else {
            stripeClient.getSubscription(subscription.id)
        }

        applySnapshot(snapshot.subscriptionId, snapshot, rawFallback = subscription.toJson())
    }

    private fun handleInvoiceEvent(type: String, invoice: Invoice) {
        val subscriptionId = invoice.parent?.subscriptionDetails?.subscription
            ?: run {
                log.info("Stripe invoice event without subscription id type={}", type)
                return
            }

        val snapshot = stripeClient.getSubscription(subscriptionId)
        val status = when (type) {
            "invoice.payment_failed",
            "invoice.payment_action_required",
            -> SubscriptionStatus.BILLING_RETRY
            else -> snapshot.status
        }

        val userId = subscriptionService.resolveUserId(
            provider = PaymentProvider.STRIPE,
            providerSubscriptionId = subscriptionId,
            appAccountToken = snapshot.userIdMetadata,
        )
        paymentTransactionService.upsertFromStripeInvoice(
            userId = userId,
            invoice = invoice,
            eventType = type,
            providerSubscriptionId = subscriptionId,
        )

        applySnapshot(
            subscriptionId = snapshot.subscriptionId,
            snapshot = snapshot.copy(status = status),
            rawFallback = invoice.toJson(),
        )
    }

    private fun applySnapshot(
        subscriptionId: String,
        snapshot: com.fancia.backend.payment.core.support.stripe.StripeSubscriptionSnapshot,
        rawFallback: String?,
    ) {
        val userId = subscriptionService.resolveUserId(
            provider = PaymentProvider.STRIPE,
            providerSubscriptionId = subscriptionId,
            appAccountToken = snapshot.userIdMetadata,
        )

        log.info(
            "Stripe webhook subscriptionId={} stripeStatus={} status={} userId={} productId={} env={}",
            subscriptionId,
            snapshot.stripeStatus,
            snapshot.status,
            userId,
            snapshot.productId,
            snapshot.environment,
        )

        when (snapshot.status) {
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.GRACE_PERIOD,
            SubscriptionStatus.BILLING_RETRY,
            SubscriptionStatus.CANCELLED,
            -> {
                subscriptionService.activateSubscription(
                    userId = userId,
                    provider = PaymentProvider.STRIPE,
                    providerSubscriptionId = subscriptionId,
                    productId = snapshot.productId,
                    expiresAt = snapshot.expiresAt,
                    status = snapshot.status,
                    environment = snapshot.environment,
                    rawData = snapshot.rawJson ?: rawFallback,
                )
            }
            SubscriptionStatus.EXPIRED,
            SubscriptionStatus.REFUNDED,
            -> {
                subscriptionService.deactivateSubscription(
                    provider = PaymentProvider.STRIPE,
                    providerSubscriptionId = subscriptionId,
                    reason = snapshot.status,
                    rawData = snapshot.rawJson ?: rawFallback,
                )
            }
        }
    }

    private fun deserializeSubscription(event: com.stripe.model.Event): Subscription {
        val deserializer = event.dataObjectDeserializer
        val subscription = deserializer.`object`.orElse(null) as? Subscription
            ?: deserializer.deserializeUnsafe() as? Subscription
            ?: throw InvalidStripeNotificationException(message = "Stripe event missing Subscription object")
        return subscription
    }

    private fun deserializeAccount(event: com.stripe.model.Event): Account {
        val deserializer = event.dataObjectDeserializer
        val account = deserializer.`object`.orElse(null) as? Account
            ?: deserializer.deserializeUnsafe() as? Account
            ?: throw InvalidStripeNotificationException(message = "Stripe event missing Account object")
        return account
    }

    private fun deserializeInvoice(event: com.stripe.model.Event): Invoice {
        val deserializer = event.dataObjectDeserializer
        val invoice = deserializer.`object`.orElse(null) as? Invoice
            ?: deserializer.deserializeUnsafe() as? Invoice
            ?: throw InvalidStripeNotificationException(message = "Stripe event missing Invoice object")
        return invoice
    }

    private fun deserializeCheckoutSession(event: com.stripe.model.Event): com.stripe.model.checkout.Session {
        val deserializer = event.dataObjectDeserializer
        val session = deserializer.`object`.orElse(null) as? com.stripe.model.checkout.Session
            ?: deserializer.deserializeUnsafe() as? com.stripe.model.checkout.Session
            ?: throw InvalidStripeNotificationException(message = "Stripe event missing Checkout Session object")
        return session
    }
}
