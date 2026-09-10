package com.fancia.backend.payment.external

import com.fancia.backend.payment.config.FeignConfig
import com.fancia.backend.shared.payment.core.dto.ConfirmConnectCheckoutPaidRequest
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import java.util.UUID

@FeignClient(
    name = "venue-internal-service",
    path = "/internal/v1/venue-bookings",
    configuration = [FeignConfig::class],
)
interface VenueBookingInternalClient {
    @PostMapping("/{bookingId}/paid")
    fun confirmPaid(
        @PathVariable bookingId: UUID,
        @RequestBody request: ConfirmConnectCheckoutPaidRequest?,
    )
}
