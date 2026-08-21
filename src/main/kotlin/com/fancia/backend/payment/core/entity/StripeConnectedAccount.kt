package com.fancia.backend.payment.core.entity

import com.fancia.backend.shared.user.core.entity.User
import com.fancia.backend.shared.user.core.entity.UserConnectedAccount
import com.fancia.backend.shared.user.core.enums.ConnectedAccountProvider
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Table
import org.hibernate.annotations.UpdateTimestamp
import java.time.LocalDateTime

@Entity
@Table(name = "stripe_connected_accounts")
class StripeConnectedAccount(
    providerId: String?,
    user: User?,
) : UserConnectedAccount(ConnectedAccountProvider.STRIPE.value, providerId, user) {
    @Column(length = 2)
    var country: String? = null

    @Column(name = "default_currency", length = 8)
    var defaultCurrency: String? = null

    @Column(name = "charges_enabled", nullable = false)
    var chargesEnabled: Boolean = false

    @Column(name = "payouts_enabled", nullable = false)
    var payoutsEnabled: Boolean = false

    @Column(name = "details_submitted", nullable = false)
    var detailsSubmitted: Boolean = false

    @Column(name = "disabled_reason")
    var disabledReason: String? = null

    @Column(name = "onboarded_at")
    var onboardedAt: LocalDateTime? = null

    @Column(name = "raw_payload", columnDefinition = "text")
    var rawPayload: String? = null

    @UpdateTimestamp
    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null
}
