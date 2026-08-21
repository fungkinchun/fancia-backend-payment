package com.fancia.backend.payment.core.service

import com.fancia.backend.payment.config.ApplicationProperties
import com.fancia.backend.payment.core.repository.SubscriptionRepository
import com.fancia.backend.shared.user.core.enums.SubscriptionStatus
import org.springframework.stereotype.Component
import java.util.UUID
import kotlin.math.roundToLong

@Component
class PlatformFeeCalculator(
    private val applicationProperties: ApplicationProperties,
    private val subscriptionRepository: SubscriptionRepository,
) {
    private val premiumStatuses = setOf(
        SubscriptionStatus.ACTIVE,
        SubscriptionStatus.GRACE_PERIOD,
        SubscriptionStatus.BILLING_RETRY,
        SubscriptionStatus.CANCELLED,
    )

    fun applicationFeeMinor(sellerUserId: UUID, amountMinor: Long): Long {
        if (amountMinor <= 0L) return 0L
        val percent = if (sellerHasPremium(sellerUserId)) {
            applicationProperties.stripe.connect.premiumPlatformFeePercent
        } else {
            applicationProperties.stripe.connect.platformFeePercent
        }
        val fee = (amountMinor * percent / 100.0).roundToLong()
        return fee.coerceIn(0L, (amountMinor - 1L).coerceAtLeast(0L))
    }

    private fun sellerHasPremium(userId: UUID): Boolean {
        val best = subscriptionRepository
            .findFirstByUserIdAndStatusInOrderByExpiresAtDesc(userId, premiumStatuses)
            .orElse(null)
            ?: return false
        return when (best.status) {
            SubscriptionStatus.ACTIVE, SubscriptionStatus.GRACE_PERIOD, SubscriptionStatus.BILLING_RETRY -> true
            SubscriptionStatus.CANCELLED ->
                best.expiresAt?.isAfter(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC)) == true
            else -> false
        }
    }
}
