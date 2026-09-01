package com.fancia.backend.payment.core.controller

import com.fancia.backend.payment.core.service.SubscriptionService
import com.fancia.backend.shared.user.core.dto.GrantReferralPremiumRequest
import com.fancia.backend.shared.user.core.dto.GrantReferralPremiumResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/internal/subscriptions")
@Tag(name = "Subscriptions (internal)", description = "Service-to-service subscription grants")
class SubscriptionInternalController(
    private val subscriptionService: SubscriptionService,
) {
    @Operation(summary = "Grant or extend complimentary referral Premium")
    @PostMapping("/referral")
    fun grantReferralPremium(
        @RequestBody @Valid request: GrantReferralPremiumRequest,
    ): ResponseEntity<GrantReferralPremiumResponse> =
        ResponseEntity.ok(
            subscriptionService.grantReferralPremium(
                userId = request.userId,
                days = request.days.takeIf { it > 0 } ?: 30,
            ),
        )
}
