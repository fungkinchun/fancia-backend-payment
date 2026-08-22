package com.fancia.backend.payment.external

import com.fancia.backend.payment.config.FeignConfig
import com.fancia.backend.shared.payment.core.dto.ConfirmConnectCheckoutPaidRequest
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import java.util.UUID

@FeignClient(
    name = "event-internal-service",
    path = "/internal",
    configuration = [FeignConfig::class],
)
interface EventReservationInternalClient {
    @PostMapping("/events/{eventId}/occurrences/{occurrenceId}/reservations/{userId}/paid")
    fun confirmPaid(
        @PathVariable eventId: UUID,
        @PathVariable occurrenceId: UUID,
        @PathVariable userId: UUID,
        @RequestBody request: ConfirmConnectCheckoutPaidRequest?,
    )
}
