package com.fancia.backend.payment.core.controller

import com.fancia.backend.payment.core.service.ConnectAccountService
import com.fancia.backend.shared.payment.core.dto.PayoutReadinessResponse
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/internal/v1/connect/accounts")
@Tag(name = "Connect Accounts (internal)", description = "Service-to-service payout readiness checks")
class ConnectAccountInternalController(
    private val connectAccountService: ConnectAccountService,
) {
    @Operation(
        summary = "Check whether a user can receive money",
        description = "Answers with payoutsReady=false when the user has no Connect account, so callers can gate publishing a priced listing without handling a 404.",
    )
    @GetMapping("/{userId}")
    fun payoutReadiness(@PathVariable userId: UUID): ResponseEntity<PayoutReadinessResponse> =
        ResponseEntity.ok(connectAccountService.payoutReadiness(userId))
}
