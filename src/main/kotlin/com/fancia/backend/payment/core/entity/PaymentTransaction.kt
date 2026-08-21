package com.fancia.backend.payment.core.entity

import com.fancia.backend.shared.common.core.entity.AbstractEntity
import com.fancia.backend.shared.user.core.enums.PaymentProvider
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime
import java.util.UUID

@Entity
@Table(name = "payment_transactions")
class PaymentTransaction : AbstractEntity() {
    @Column(name = "user_id", nullable = false)
    var userId: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    var provider: PaymentProvider? = null

    @Column(name = "provider_transaction_id", nullable = false)
    var providerTransactionId: String? = null

    @Column(name = "provider_subscription_id")
    var providerSubscriptionId: String? = null

    @Column(name = "amount_cents", nullable = false)
    var amountCents: Long = 0

    @Column(nullable = false, length = 8)
    var currency: String = "gbp"

    @Column(nullable = false, length = 32)
    var status: String = "OPEN"

    @Column(length = 512)
    var description: String? = null

    @Column(name = "invoice_url", length = 1024)
    var invoiceUrl: String? = null

    @Column(name = "paid_at")
    var paidAt: LocalDateTime? = null

    @Column(name = "raw_payload", columnDefinition = "text")
    var rawPayload: String? = null

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null
}
