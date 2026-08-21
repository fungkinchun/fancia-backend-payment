package com.fancia.backend.payment.core.entity

import com.fancia.backend.shared.common.core.entity.AbstractEntity
import com.fancia.backend.shared.user.core.enums.PaymentProvider
import com.fancia.backend.shared.user.core.enums.SubscriptionStatus
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "subscriptions")
class Subscription : AbstractEntity() {
    @Column(name = "user_id", nullable = false)
    var userId: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var provider: PaymentProvider? = null

    @Column(name = "provider_subscription_id", nullable = false)
    var providerSubscriptionId: String? = null

    @Column(name = "product_id")
    var productId: String? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    var status: SubscriptionStatus = SubscriptionStatus.ACTIVE

    @Column(name = "expires_at")
    var expiresAt: LocalDateTime? = null

    @Column(length = 32)
    var environment: String? = null

    @Column(name = "raw_payload", columnDefinition = "text")
    var rawPayload: String? = null

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null
}
