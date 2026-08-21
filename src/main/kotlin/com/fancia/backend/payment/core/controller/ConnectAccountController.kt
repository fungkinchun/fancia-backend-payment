package com.fancia.backend.payment.core.controller

import com.fancia.backend.payment.core.service.ConnectAccountService
import com.fancia.backend.shared.common.core.exception.InvalidAuthenticationException
import com.fancia.backend.shared.payment.core.dto.ConnectAccountResponse
import com.fancia.backend.shared.payment.core.dto.ConnectLinkResponse
import com.fancia.backend.shared.payment.core.dto.ConnectOnboardingRequest
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
@RequestMapping("/api/connect/accounts")
@Tag(
    name = "Connect Accounts",
    description = "Stripe Connect Express accounts used to pay venue owners and event hosts",
)
@SecurityRequirement(name = "bearerAuth")
class ConnectAccountController(
    private val connectAccountService: ConnectAccountService,
) {
    @Operation(
        summary = "Start or resume Stripe onboarding",
        description = "Creates the Express account on first call, then returns a hosted onboarding URL. Open it in a browser and return via returnUrl; refreshUrl is used when the link expires.",
    )
    @PostMapping("/onboarding")
    fun onboarding(
        @RequestBody @Valid request: ConnectOnboardingRequest,
        @AuthenticationPrincipal jwt: Jwt,
    ): ResponseEntity<ConnectLinkResponse> =
        ResponseEntity.ok(connectAccountService.startOnboarding(jwt.userId(), request))

    @Operation(
        summary = "Get payout account status",
        description = "Returns the locally cached capability flags, kept current by the account.updated webhook.",
    )
    @GetMapping("/me")
    fun me(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<ConnectAccountResponse> =
        ResponseEntity.ok(connectAccountService.status(jwt.userId()))

    @Operation(
        summary = "Re-read payout account status from Stripe",
        description = "Use after returning from onboarding so the user sees the result without waiting for the webhook.",
    )
    @PostMapping("/me/refresh")
    fun refresh(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<ConnectAccountResponse> =
        ResponseEntity.ok(connectAccountService.refreshStatus(jwt.userId()))

    @Operation(
        summary = "Open the Stripe Express dashboard",
        description = "Returns a single-use login link where the user can see balances and payouts.",
    )
    @PostMapping("/me/dashboard")
    fun dashboard(@AuthenticationPrincipal jwt: Jwt): ResponseEntity<ConnectLinkResponse> =
        ResponseEntity.ok(connectAccountService.dashboardLink(jwt.userId()))

    private fun Jwt.userId(): UUID =
        getClaimAsString("userId")?.let { UUID.fromString(it) }
            ?: throw InvalidAuthenticationException()
}
