package com.fancia.backend.payment.core.repository

import com.fancia.backend.payment.core.entity.WebhookEvent
import com.fancia.backend.shared.user.core.enums.PaymentProvider
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface WebhookEventRepository : JpaRepository<WebhookEvent, UUID> {
    fun findByProviderAndEventId(
        provider: PaymentProvider,
        eventId: String,
    ): Optional<WebhookEvent>

    fun existsByProviderAndEventId(
        provider: PaymentProvider,
        eventId: String,
    ): Boolean
}
