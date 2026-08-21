package com.fancia.backend.payment.core.controller

import com.fancia.backend.payment.core.service.AppleWebhookService
import com.fancia.backend.payment.core.service.GoogleWebhookService
import com.fancia.backend.payment.core.service.StripeWebhookService
import com.fancia.backend.shared.user.core.dto.AppleWebhookRequest
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/webhooks")
@Tag(name = "Webhooks", description = "Provider subscription webhooks (Apple, Google, Stripe)")
class WebhookController(
    private val appleWebhookService: AppleWebhookService,
    private val googleWebhookService: GoogleWebhookService,
    private val stripeWebhookService: StripeWebhookService,
) {
    @Operation(summary = "Apple App Store Server Notifications V2")
    @PostMapping("/apple")
    fun apple(@RequestBody @Valid request: AppleWebhookRequest): ResponseEntity<Void> {
        appleWebhookService.handleSignedPayload(request.signedPayload)
        return ResponseEntity.ok().build()
    }

    @Operation(
        summary = "Google Play Real-time Developer Notifications (Pub/Sub push)",
        description = "Receives Pub/Sub push envelopes. Verifies Google OIDC Bearer, then verifies subscription state via Play Developer API.",
    )
    @PostMapping("/google")
    fun google(
        @RequestBody rawBody: String,
        @RequestHeader(name = "Authorization", required = false) authorization: String?,
    ): ResponseEntity<Void> {
        googleWebhookService.handle(rawBody, authorization)
        return ResponseEntity.ok().build()
    }

    @Operation(
        summary = "Stripe webhooks",
        description = "Verifies Stripe-Signature, then retrieves Subscription via Stripe API and updates premium status.",
    )
    @PostMapping("/stripe")
    fun stripe(
        @RequestBody rawBody: String,
        @RequestHeader(name = "Stripe-Signature", required = false) stripeSignature: String?,
    ): ResponseEntity<Void> {
        stripeWebhookService.handle(rawBody, stripeSignature)
        return ResponseEntity.ok().build()
    }
}
