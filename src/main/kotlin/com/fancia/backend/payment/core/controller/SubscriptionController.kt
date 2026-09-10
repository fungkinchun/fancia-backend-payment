package com.fancia.backend.payment.core.controller

import com.fancia.backend.payment.core.service.PaymentTransactionService
import com.fancia.backend.payment.core.service.SubscriptionManagementService
import com.fancia.backend.payment.core.service.SubscriptionService
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.payment.core.dto.CheckoutSubscriptionRequest
import com.fancia.backend.shared.payment.core.dto.CheckoutSubscriptionResponse
import com.fancia.backend.shared.payment.core.dto.LinkSubscriptionRequest
import com.fancia.backend.shared.payment.core.dto.ManageSubscriptionRequest
import com.fancia.backend.shared.payment.core.dto.ManageSubscriptionResponse
import com.fancia.backend.shared.payment.core.dto.PaymentTransactionResponse
import com.fancia.backend.shared.user.core.dto.SubscriptionResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.oauth2.jwt.Jwt
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/v1/subscriptions")
@Tag(name = "Subscriptions", description = "Provider-agnostic subscription management and status")
@SecurityRequirement(name = "bearerAuth")
class SubscriptionController(
    private val subscriptionService: SubscriptionService,
    private val subscriptionManagementService: SubscriptionManagementService,
    private val paymentTransactionService: PaymentTransactionService,
) {
    @Operation(summary = "List current user subscriptions")
    @GetMapping("/me")
    fun mySubscriptions(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<List<SubscriptionResponse>> {
        val userId = jwt.userId()
        return ResponseEntity.ok(subscriptionService.findByUserId(userId))
    }

    @Operation(
        summary = "List payment transactions",
        description = "Invoice / charge history for the current user (synced from Stripe when available).",
    )
    @GetMapping("/transactions")
    fun myTransactions(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<List<PaymentTransactionResponse>> =
        ResponseEntity.ok(paymentTransactionService.listForUser(jwt.userId()))

    @Operation(
        summary = "Start subscription checkout",
        description = "Returns a hosted checkout URL for the web billing provider (Stripe). Apple/Google purchases happen in-store, then call /link.",
    )
    @PostMapping("/checkout")
    fun checkout(
        @RequestBody @Valid request: CheckoutSubscriptionRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<CheckoutSubscriptionResponse> =
        ResponseEntity.ok(subscriptionManagementService.checkout(jwt.userId(), request))

    @Operation(
        summary = "Open subscription management portal",
        description = "Returns a provider billing portal URL when supported (Stripe). App Store / Play subscriptions must be managed in-store.",
    )
    @PostMapping("/portal")
    fun portal(
        @RequestBody @Valid request: ManageSubscriptionRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ManageSubscriptionResponse> =
        ResponseEntity.ok(subscriptionManagementService.portal(jwt.userId(), request))

    @Operation(
        summary = "Cancel subscription at period end",
        description = "Cancels renewal for the user's active subscription when the provider supports server-side cancel (Stripe). Premium access continues until expiresAt.",
    )
    @PostMapping("/cancel")
    fun cancel(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<SubscriptionResponse> =
        ResponseEntity.ok(subscriptionManagementService.cancel(jwt.userId()))

    @Operation(
        summary = "Link an external purchase to the current user",
        description = "providerSubscriptionId is Stripe sub_…, Google purchaseToken, or Apple originalTransactionId.",
    )
    @PostMapping("/link")
    fun link(
        @RequestBody @Valid request: LinkSubscriptionRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<SubscriptionResponse> =
        ResponseEntity.ok(subscriptionManagementService.link(jwt.userId(), request))

    private fun Jwt.userId(): UUID =
        getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
}
