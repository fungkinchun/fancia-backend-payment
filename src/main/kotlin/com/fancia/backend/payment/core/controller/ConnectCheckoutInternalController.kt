package com.fancia.backend.payment.core.controller

import com.fancia.backend.payment.core.service.ConnectCheckoutService
import com.fancia.backend.payment.core.support.stripe.StripeClient
import com.fancia.backend.shared.payment.core.dto.ConnectCheckoutResponse
import com.fancia.backend.shared.payment.core.dto.CreateConnectCheckoutSessionRequest
import com.fancia.backend.shared.payment.core.dto.RefundConnectCheckoutRequest
import io.swagger.v3.oas.annotations.Hidden
import io.swagger.v3.oas.annotations.Operation
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/checkout")
@Hidden
class ConnectCheckoutInternalController(
    private val connectCheckoutService: ConnectCheckoutService,
    private val stripeClient: StripeClient,
) {
    @Operation(summary = "Create a destination-charge Checkout Session from a domain service")
    @PostMapping("/sessions")
    fun createSession(
        @RequestBody @Valid request: CreateConnectCheckoutSessionRequest,
    ): ResponseEntity<ConnectCheckoutResponse> =
        ResponseEntity.ok(connectCheckoutService.createSession(request))

    @Operation(summary = "Refund a Connect destination Checkout Session (reverse transfer + app fee)")
    @PostMapping("/refunds")
    fun refund(@RequestBody @Valid request: RefundConnectCheckoutRequest): ResponseEntity<Void> {
        stripeClient.refundConnectCheckoutSession(request.checkoutSessionId)
        return ResponseEntity.noContent().build()
    }
}
