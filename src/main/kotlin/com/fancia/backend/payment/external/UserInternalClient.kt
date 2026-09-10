package com.fancia.backend.payment.external

import com.fancia.backend.payment.config.FeignConfig
import com.fancia.backend.shared.user.core.dto.UpdatePremiumStatusRequest
import com.fancia.backend.shared.user.core.dto.UserResponse
import org.springframework.cloud.openfeign.FeignClient
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import java.util.UUID

@FeignClient(
    name = "user-internal-service",
    path = "/internal/v1",
    configuration = [FeignConfig::class],
)
interface UserInternalClient {
    @PutMapping("/users/{id}/premium")
    fun updatePremiumStatus(
        @PathVariable id: UUID,
        @RequestBody request: UpdatePremiumStatusRequest,
    ): UserResponse
}
