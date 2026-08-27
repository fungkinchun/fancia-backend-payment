package com.fancia.backend.payment.core.service

import com.fancia.backend.payment.core.entity.PaymentTransaction
import com.fancia.backend.payment.core.repository.PaymentTransactionRepository
import com.fancia.backend.payment.core.repository.SubscriptionRepository
import com.fancia.backend.payment.core.support.stripe.StripeClient
import com.fancia.backend.shared.payment.core.dto.PaymentTransactionResponse
import com.fancia.backend.shared.user.core.enums.PaymentProvider
import com.stripe.model.Invoice
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

@Service
class PaymentTransactionService(
    private val paymentTransactionRepository: PaymentTransactionRepository,
    private val subscriptionRepository: SubscriptionRepository,
    private val stripeClient: StripeClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun listForUser(userId: UUID): List<PaymentTransactionResponse> {
        syncStripeInvoices(userId)
        return paymentTransactionRepository
            .findByUserIdOrderByPaidAtDescCreatedAtDesc(userId)
            .map { it.toResponse() }
    }

    @Transactional
    fun upsertFromStripeInvoice(
        userId: UUID?,
        invoice: Invoice,
        eventType: String? = null,
        providerSubscriptionId: String? = null,
    ) {
        if (userId == null) {
            log.info("Skipping payment transaction upsert — no userId for invoice={}", invoice.id)
            return
        }
        val invoiceId = invoice.id ?: return
        val existing = paymentTransactionRepository
            .findByProviderAndProviderTransactionId(PaymentProvider.STRIPE, invoiceId)
            .orElse(null)

        val tx = existing ?: PaymentTransaction().apply {
            this.userId = userId
            this.provider = PaymentProvider.STRIPE
            this.providerTransactionId = invoiceId
            this.createdBy = userId
        }

        tx.userId = userId
        tx.providerSubscriptionId = providerSubscriptionId
            ?: invoice.parent?.subscriptionDetails?.subscription
            ?: tx.providerSubscriptionId
        tx.amountCents = invoice.amountPaid
            ?.takeIf { it > 0 }
            ?: invoice.amountDue
            ?: invoice.total
            ?: 0L
        tx.currency = invoice.currency?.lowercase() ?: "gbp"
        tx.status = mapInvoiceStatus(invoice.status, eventType)
        tx.description = invoice.number?.let { "Invoice $it" } ?: "Fancia Premium"
        tx.invoiceUrl = invoice.hostedInvoiceUrl
        tx.paidAt = invoice.statusTransitions?.paidAt
            ?.takeIf { it > 0 }
            ?.let { LocalDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneOffset.UTC) }
            ?: invoice.created
                ?.takeIf { it > 0 && tx.status == "PAID" }
                ?.let { LocalDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneOffset.UTC) }
        tx.rawPayload = invoice.toJson()

        paymentTransactionRepository.save(tx)
        log.error(
            "Payment transaction upserted source=invoice id={} userId={} providerTxId={} " +
                "amountCents={} currency={} status={}",
            tx.id,
            tx.userId,
            tx.providerTransactionId,
            tx.amountCents,
            tx.currency,
            tx.status,
        )
    }

    @Transactional
    fun upsertFromConnectCheckoutSession(
        userId: UUID,
        session: com.stripe.model.checkout.Session,
        purpose: String,
    ) {
        val sessionId = session.id ?: return
        val existing = paymentTransactionRepository
            .findByProviderAndProviderTransactionId(PaymentProvider.STRIPE, sessionId)
            .orElse(null)

        val tx = existing ?: PaymentTransaction().apply {
            this.userId = userId
            this.provider = PaymentProvider.STRIPE
            this.providerTransactionId = sessionId
            this.createdBy = userId
        }

        tx.userId = userId
        tx.amountCents = session.amountTotal ?: 0L
        tx.currency = session.currency?.lowercase() ?: "gbp"
        tx.status = when (session.paymentStatus) {
            "paid" -> "PAID"
            "unpaid" -> "OPEN"
            "no_payment_required" -> "PAID"
            else -> session.paymentStatus?.uppercase() ?: "OPEN"
        }
        tx.description = when (purpose) {
            "VENUE_BOOKING" -> "Venue booking"
            "EVENT_TICKET" -> "Event ticket"
            else -> purpose
        }
        tx.invoiceUrl = session.url
        if (tx.status == "PAID" && tx.paidAt == null) {
            tx.paidAt = LocalDateTime.now(ZoneOffset.UTC)
        }
        tx.rawPayload = session.toJson()
        paymentTransactionRepository.save(tx)
        log.error(
            "Payment transaction upserted source=connect_checkout id={} userId={} providerTxId={} " +
                "purpose={} amountCents={} currency={} status={}",
            tx.id,
            tx.userId,
            tx.providerTransactionId,
            purpose,
            tx.amountCents,
            tx.currency,
            tx.status,
        )
    }

    @Transactional
    fun markConnectCheckoutRefunded(checkoutSessionId: String) {
        val tx = paymentTransactionRepository
            .findByProviderAndProviderTransactionId(PaymentProvider.STRIPE, checkoutSessionId)
            .orElse(null)
            ?: return
        tx.status = "REFUNDED"
        paymentTransactionRepository.save(tx)
        log.error(
            "Payment transaction refunded id={} userId={} providerTxId={} amountCents={} currency={}",
            tx.id,
            tx.userId,
            tx.providerTransactionId,
            tx.amountCents,
            tx.currency,
        )
    }

    private fun syncStripeInvoices(userId: UUID) {
        val stripeSub = subscriptionRepository.findByUserId(userId)
            .firstOrNull { it.provider == PaymentProvider.STRIPE && !it.providerSubscriptionId.isNullOrBlank() }
            ?: return

        val customerId = runCatching {
            stripeClient.getSubscription(stripeSub.providerSubscriptionId!!).customerId
        }.getOrNull() ?: return

        val invoices = runCatching { stripeClient.listInvoices(customerId) }
            .onFailure { log.warn("Stripe invoice sync failed userId={}", userId, it) }
            .getOrDefault(emptyList())

        invoices.forEach { invoice ->

            if ((invoice.total ?: 0L) == 0L && (invoice.amountPaid ?: 0L) == 0L && invoice.status == "draft") {
                return@forEach
            }
            upsertFromStripeInvoice(
                userId = userId,
                invoice = invoice,
                providerSubscriptionId = stripeSub.providerSubscriptionId,
            )
        }
    }

    private fun mapInvoiceStatus(stripeStatus: String?, eventType: String?): String {
        if (eventType == "invoice.payment_failed" || eventType == "invoice.payment_action_required") {
            return "FAILED"
        }
        return when (stripeStatus) {
            "paid" -> "PAID"
            "open" -> "OPEN"
            "void" -> "VOID"
            "uncollectible" -> "FAILED"
            "draft" -> "DRAFT"
            else -> stripeStatus?.uppercase() ?: "OPEN"
        }
    }

    private fun PaymentTransaction.toResponse() =
        PaymentTransactionResponse(
            id = id,
            provider = provider,
            providerTransactionId = providerTransactionId,
            amountCents = amountCents,
            currency = currency,
            status = status,
            description = description,
            invoiceUrl = invoiceUrl,
            paidAt = paidAt,
            createdAt = createdAt,
        )
}
