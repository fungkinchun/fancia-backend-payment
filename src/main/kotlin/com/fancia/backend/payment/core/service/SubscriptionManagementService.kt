package com.fancia.backend.payment.core.service

import com.fancia.backend.payment.config.ApplicationProperties
import com.fancia.backend.payment.core.support.google.GooglePlayDeveloperClient
import com.fancia.backend.payment.core.support.stripe.StripeClient
import com.fancia.backend.shared.payment.core.dto.CheckoutSubscriptionRequest
import com.fancia.backend.shared.payment.core.dto.CheckoutSubscriptionResponse
import com.fancia.backend.shared.payment.core.dto.LinkSubscriptionRequest
import com.fancia.backend.shared.payment.core.dto.ManageSubscriptionRequest
import com.fancia.backend.shared.payment.core.dto.ManageSubscriptionResponse
import com.fancia.backend.shared.payment.core.exception.SubscriptionBillingException
import com.fancia.backend.shared.payment.core.exception.SubscriptionOperationNotSupportedException
import com.fancia.backend.shared.user.core.dto.SubscriptionResponse
import com.fancia.backend.shared.user.core.enums.PaymentProvider
import com.fancia.backend.shared.user.core.enums.SubscriptionStatus
import com.fancia.backend.shared.user.core.exception.SubscriptionNotFoundException
import org.springframework.stereotype.Service
import java.net.URI
import java.util.UUID

@Service
class SubscriptionManagementService(
    private val subscriptionService: SubscriptionService,
    private val stripeClient: StripeClient,
    private val googlePlayDeveloperClient: GooglePlayDeveloperClient,
    private val applicationProperties: ApplicationProperties,
) {
    private val cancellableStatuses = setOf(
        SubscriptionStatus.ACTIVE,
        SubscriptionStatus.GRACE_PERIOD,
        SubscriptionStatus.BILLING_RETRY,
    )

    private val manageableStatuses = cancellableStatuses + SubscriptionStatus.CANCELLED

    fun checkout(userId: UUID, request: CheckoutSubscriptionRequest): CheckoutSubscriptionResponse {
        validateRedirectUrl(request.successUrl, "successUrl", PaymentProvider.STRIPE)
        validateRedirectUrl(request.cancelUrl, "cancelUrl", PaymentProvider.STRIPE)

        val (url, sessionId) = stripeClient.createCheckoutSession(
            userId = userId,
            successUrl = request.successUrl,
            cancelUrl = request.cancelUrl,
        )
        return CheckoutSubscriptionResponse(
            url = url,
            sessionId = sessionId,
            provider = PaymentProvider.STRIPE,
        )
    }

    fun portal(userId: UUID, request: ManageSubscriptionRequest): ManageSubscriptionResponse {
        val sub = findManagedSubscription(userId)
            ?: throw SubscriptionNotFoundException(message = "No subscription found to manage")
        val provider = sub.provider
            ?: throw SubscriptionNotFoundException(message = "Subscription provider missing")

        validateRedirectUrl(request.returnUrl, "returnUrl", provider)

        return when (provider) {
            PaymentProvider.STRIPE -> {
                val providerSubscriptionId = sub.providerSubscriptionId
                    ?: throw SubscriptionNotFoundException(message = "Stripe subscription id missing")
                val customerId = stripeClient.getSubscription(providerSubscriptionId).customerId
                    ?: throw SubscriptionBillingException(
                        provider = provider,
                        message = "Subscription has no customer id for provider: $provider",
                    )
                val url = stripeClient.createBillingPortalSession(customerId, request.returnUrl)
                ManageSubscriptionResponse(url = url, provider = PaymentProvider.STRIPE)
            }
            PaymentProvider.APPLE ->
                throw SubscriptionOperationNotSupportedException(
                    provider = PaymentProvider.APPLE,
                    operation = "Billing portal",
                )
            PaymentProvider.GOOGLE ->
                throw SubscriptionOperationNotSupportedException(
                    provider = PaymentProvider.GOOGLE,
                    operation = "Billing portal",
                )
            PaymentProvider.REFERRAL ->
                throw SubscriptionOperationNotSupportedException(
                    provider = PaymentProvider.REFERRAL,
                    operation = "Billing portal",
                )
        }
    }

    fun cancel(userId: UUID): SubscriptionResponse {
        val sub = subscriptionService.findByUserId(userId)
            .firstOrNull { it.status in cancellableStatuses }
            ?: throw SubscriptionNotFoundException(message = "No cancellable subscription found for user")

        return when (sub.provider) {
            PaymentProvider.STRIPE -> {
                val providerSubscriptionId = sub.providerSubscriptionId
                    ?: throw SubscriptionNotFoundException(message = "Stripe subscription id missing")
                val snapshot = stripeClient.cancelSubscriptionAtPeriodEnd(providerSubscriptionId)
                subscriptionService.activateSubscription(
                    userId = userId,
                    provider = PaymentProvider.STRIPE,
                    providerSubscriptionId = snapshot.subscriptionId,
                    productId = snapshot.productId,
                    expiresAt = snapshot.expiresAt,
                    status = snapshot.status,
                    environment = snapshot.environment,
                    rawData = snapshot.rawJson,
                ) ?: throw SubscriptionBillingException(PaymentProvider.STRIPE)
            }
            PaymentProvider.APPLE ->
                throw SubscriptionOperationNotSupportedException(
                    provider = PaymentProvider.APPLE,
                    operation = "Cancel",
                )
            PaymentProvider.GOOGLE ->
                throw SubscriptionOperationNotSupportedException(
                    provider = PaymentProvider.GOOGLE,
                    operation = "Cancel",
                )
            PaymentProvider.REFERRAL ->
                throw SubscriptionOperationNotSupportedException(
                    provider = PaymentProvider.REFERRAL,
                    operation = "Cancel",
                )
            null -> throw SubscriptionNotFoundException(message = "Subscription provider missing")
        }
    }

    fun link(userId: UUID, request: LinkSubscriptionRequest): SubscriptionResponse =
        when (request.provider) {
            PaymentProvider.APPLE ->
                subscriptionService.linkProviderSubscription(
                    userId = userId,
                    provider = PaymentProvider.APPLE,
                    providerSubscriptionId = request.providerSubscriptionId,
                    productId = request.productId,
                )
            PaymentProvider.GOOGLE -> {
                val snapshot = googlePlayDeveloperClient.getSubscription(request.providerSubscriptionId)
                subscriptionService.linkProviderSubscription(
                    userId = userId,
                    provider = PaymentProvider.GOOGLE,
                    providerSubscriptionId = snapshot.purchaseToken,
                    productId = snapshot.productId ?: request.productId,
                    environment = snapshot.environment,
                )
                subscriptionService.activateSubscription(
                    userId = userId,
                    provider = PaymentProvider.GOOGLE,
                    providerSubscriptionId = snapshot.purchaseToken,
                    productId = snapshot.productId ?: request.productId,
                    expiresAt = snapshot.expiresAt,
                    status = snapshot.status,
                    environment = snapshot.environment,
                    rawData = snapshot.rawJson,
                ) ?: throw SubscriptionBillingException(PaymentProvider.GOOGLE)
            }
            PaymentProvider.STRIPE -> {
                val snapshot = stripeClient.getSubscription(request.providerSubscriptionId)
                subscriptionService.linkProviderSubscription(
                    userId = userId,
                    provider = PaymentProvider.STRIPE,
                    providerSubscriptionId = snapshot.subscriptionId,
                    productId = snapshot.productId,
                    environment = snapshot.environment,
                )
                subscriptionService.activateSubscription(
                    userId = userId,
                    provider = PaymentProvider.STRIPE,
                    providerSubscriptionId = snapshot.subscriptionId,
                    productId = snapshot.productId,
                    expiresAt = snapshot.expiresAt,
                    status = snapshot.status,
                    environment = snapshot.environment,
                    rawData = snapshot.rawJson,
                ) ?: throw SubscriptionBillingException(PaymentProvider.STRIPE)
            }
            PaymentProvider.REFERRAL ->
                throw SubscriptionOperationNotSupportedException(
                    provider = PaymentProvider.REFERRAL,
                    operation = "Link subscription",
                )
        }

    private fun findManagedSubscription(userId: UUID): SubscriptionResponse? =
        subscriptionService.findByUserId(userId)
            .filter { it.status in manageableStatuses }
            .sortedWith(
                compareByDescending<SubscriptionResponse> {
                    when (it.status) {
                        SubscriptionStatus.ACTIVE -> 3
                        SubscriptionStatus.GRACE_PERIOD, SubscriptionStatus.BILLING_RETRY -> 2
                        SubscriptionStatus.CANCELLED -> 1
                        else -> 0
                    }
                }.thenByDescending { it.expiresAt?.toString().orEmpty() },
            )
            .firstOrNull()

    private fun validateRedirectUrl(url: String, field: String, provider: PaymentProvider) {
        val uri = try {
            URI(url)
        } catch (_: Exception) {
            throw SubscriptionBillingException(
                provider = provider,
                message = "$field is not a valid URL for provider: $provider",
            )
        }
        if (uri.scheme != "http" && uri.scheme != "https") {
            throw SubscriptionBillingException(
                provider = provider,
                message = "$field must be an http(s) URL for provider: $provider",
            )
        }
        val origin = "${uri.scheme}://${uri.authority}"
        val allowed = applicationProperties.allowedOrigins
        if (allowed.isNotEmpty() && allowed.none { origin.equals(it.trimEnd('/'), ignoreCase = true) }) {
            throw SubscriptionBillingException(
                provider = provider,
                message = "$field origin is not allowed for provider: $provider",
            )
        }
    }
}
