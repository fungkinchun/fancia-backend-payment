package com.fancia.backend.payment.core.entity

import com.fancia.backend.shared.common.core.entity.AbstractEntity
import com.fancia.backend.shared.user.core.enums.PaymentProvider
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import java.time.LocalDateTime

@Entity
@Table(name = "webhook_events")
class WebhookEvent : AbstractEntity() {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var provider: PaymentProvider? = null

    @Column(name = "event_id", nullable = false)
    var eventId: String? = null

    @Column(name = "event_type", length = 128)
    var eventType: String? = null

    @Column(name = "processed_at")
    var processedAt: LocalDateTime? = null

    @Column(name = "raw_payload", columnDefinition = "text")
    var rawPayload: String? = null
}
