package com.fancia.backend.payment.core.repository

import com.fancia.backend.payment.core.entity.Subscription
import com.fancia.backend.shared.user.core.enums.PaymentProvider
import com.fancia.backend.shared.user.core.enums.SubscriptionStatus
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface SubscriptionRepository : JpaRepository<Subscription, UUID> {
    fun findByProviderAndProviderSubscriptionId(
        provider: PaymentProvider,
        providerSubscriptionId: String,
    ): Optional<Subscription>

    fun findByUserId(userId: UUID): List<Subscription>

    fun findFirstByUserIdAndStatusInOrderByExpiresAtDesc(
        userId: UUID,
        statuses: Collection<SubscriptionStatus>,
    ): Optional<Subscription>
}
