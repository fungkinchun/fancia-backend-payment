package com.fancia.backend.payment.core.service

import com.fancia.backend.payment.core.entity.Subscription
import com.fancia.backend.payment.core.repository.SubscriptionRepository
import com.fancia.backend.payment.external.UserInternalClient
import com.fancia.backend.payment.mapper.toDto
import com.fancia.backend.shared.user.core.dto.SubscriptionResponse
import com.fancia.backend.shared.user.core.dto.UpdatePremiumStatusRequest
import com.fancia.backend.shared.user.core.enums.PaymentProvider
import com.fancia.backend.shared.user.core.enums.SubscriptionStatus
import com.fancia.backend.shared.user.core.exception.SubscriptionAlreadyLinkedException
import com.fancia.backend.shared.user.core.message.SubscriptionChangedEvent
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class SubscriptionService(
    private val subscriptionRepository: SubscriptionRepository,
    private val userInternalClient: UserInternalClient,
    private val kafkaTemplate: KafkaTemplate<UUID, SubscriptionChangedEvent>,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val premiumStatuses = setOf(
        SubscriptionStatus.ACTIVE,
        SubscriptionStatus.GRACE_PERIOD,
        SubscriptionStatus.BILLING_RETRY,
        SubscriptionStatus.CANCELLED,
    )

    fun findByUserId(userId: UUID): List<SubscriptionResponse> =
        subscriptionRepository.findByUserId(userId).map { it.toDto() }

    @Transactional
    fun linkProviderSubscription(
        userId: UUID,
        provider: PaymentProvider,
        providerSubscriptionId: String,
        productId: String? = null,
        environment: String? = null,
    ): SubscriptionResponse {
        val existing = subscriptionRepository
            .findByProviderAndProviderSubscriptionId(provider, providerSubscriptionId)
            .orElse(null)

        if (existing != null && existing.userId != null && existing.userId != userId) {
            throw SubscriptionAlreadyLinkedException()
        }

        val subscription = existing ?: Subscription().apply {
            this.userId = userId
            this.provider = provider
            this.providerSubscriptionId = providerSubscriptionId
            this.status = SubscriptionStatus.ACTIVE
            createdBy = userId
        }

        subscription.userId = userId
        productId?.let { subscription.productId = it }
        environment?.let { subscription.environment = it }

        val saved = subscriptionRepository.save(subscription)
        syncUserPremium(userId)
        publishChange(saved)
        return saved.toDto()
    }

    @Transactional
    fun activateSubscription(
        userId: UUID?,
        provider: PaymentProvider,
        providerSubscriptionId: String,
        productId: String?,
        expiresAt: LocalDateTime?,
        status: SubscriptionStatus = SubscriptionStatus.ACTIVE,
        environment: String? = null,
        rawData: String? = null,
    ): SubscriptionResponse? {
        val existing = subscriptionRepository
            .findByProviderAndProviderSubscriptionId(provider, providerSubscriptionId)
            .orElse(null)

        val resolvedUserId = userId ?: existing?.userId
        if (resolvedUserId == null) {
            log.warn(
                "Cannot activate subscription; no user linked for {}/{}",
                provider,
                providerSubscriptionId,
            )

            return null
        }

        if (existing != null && existing.userId != null && userId != null && existing.userId != userId) {
            throw SubscriptionAlreadyLinkedException()
        }

        val subscription = existing ?: Subscription().apply {
            this.userId = resolvedUserId
            this.provider = provider
            this.providerSubscriptionId = providerSubscriptionId
            createdBy = resolvedUserId
        }

        subscription.userId = resolvedUserId
        subscription.productId = productId ?: subscription.productId
        subscription.status = status
        subscription.expiresAt = expiresAt
        environment?.let { subscription.environment = it }
        rawData?.let { subscription.rawPayload = it }

        val saved = subscriptionRepository.save(subscription)
        syncUserPremium(resolvedUserId)
        publishChange(saved)
        log.info(
            "Activated/updated subscription userId={} provider={} id={} status={} expiresAt={}",
            resolvedUserId,
            provider,
            providerSubscriptionId,
            status,
            expiresAt,
        )
        return saved.toDto()
    }

    @Transactional
    fun updateSubscriptionStatus(
        provider: PaymentProvider,
        providerSubscriptionId: String,
        status: SubscriptionStatus,
        expiresAt: LocalDateTime? = null,
        rawData: String? = null,
    ): SubscriptionResponse? {
        val subscription = subscriptionRepository
            .findByProviderAndProviderSubscriptionId(provider, providerSubscriptionId)
            .orElse(null)
            ?: run {
                log.warn(
                    "Status update for unknown subscription {}/{} -> {}",
                    provider,
                    providerSubscriptionId,
                    status,
                )
                return null
            }

        subscription.status = status
        expiresAt?.let { subscription.expiresAt = it }
        rawData?.let { subscription.rawPayload = it }

        val saved = subscriptionRepository.save(subscription)
        saved.userId?.let { syncUserPremium(it) }
        publishChange(saved)
        return saved.toDto()
    }

    @Transactional
    fun deactivateSubscription(
        provider: PaymentProvider,
        providerSubscriptionId: String,
        reason: SubscriptionStatus,
        rawData: String? = null,
    ): SubscriptionResponse? {
        require(
            reason == SubscriptionStatus.EXPIRED ||
                reason == SubscriptionStatus.CANCELLED ||
                reason == SubscriptionStatus.REFUNDED,
        ) { "deactivateSubscription requires a terminal status, got $reason" }

        return updateSubscriptionStatus(provider, providerSubscriptionId, reason, rawData = rawData)
    }

    @Transactional
    fun deactivateSubscription(userId: UUID, reason: SubscriptionStatus): List<SubscriptionResponse> {
        val updated = subscriptionRepository.findByUserId(userId).map { subscription ->
            if (subscription.status in premiumStatuses) {
                subscription.status = reason
                subscriptionRepository.save(subscription).also { publishChange(it) }
            } else {
                subscription
            }
        }
        syncUserPremium(userId)
        return updated.map { it.toDto() }
    }

    fun resolveUserId(
        provider: PaymentProvider,
        providerSubscriptionId: String,
        appAccountToken: String?,
    ): UUID? {
        appAccountToken
            ?.takeIf { it.isNotBlank() }
            ?.let { token ->
                runCatching { UUID.fromString(token) }.getOrNull()?.let { return it }
            }

        return subscriptionRepository
            .findByProviderAndProviderSubscriptionId(provider, providerSubscriptionId)
            .orElse(null)
            ?.userId
    }

    private fun syncUserPremium(userId: UUID) {
        val now = LocalDateTime.now()
        val best = subscriptionRepository
            .findFirstByUserIdAndStatusInOrderByExpiresAtDesc(userId, premiumStatuses)
            .orElse(null)

        val premiumActive = best != null && when (best.status) {
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.GRACE_PERIOD,
            SubscriptionStatus.BILLING_RETRY,
            -> best.expiresAt == null || best.expiresAt!!.isAfter(now)
            SubscriptionStatus.CANCELLED,
            -> best.expiresAt != null && best.expiresAt!!.isAfter(now)
            else -> false
        }

        try {
            userInternalClient.updatePremiumStatus(
                userId,
                UpdatePremiumStatusRequest(
                    premiumActive = premiumActive,
                    premiumExpiresAt = best?.expiresAt,
                ),
            )
        } catch (ex: Exception) {

            log.error("Failed to sync premium status for userId={}", userId, ex)
        }
    }

    private fun publishChange(subscription: Subscription) {
        val userId = subscription.userId ?: return
        val provider = subscription.provider ?: return
        val providerSubscriptionId = subscription.providerSubscriptionId ?: return
        val now = LocalDateTime.now()
        val premiumActive = when (subscription.status) {
            SubscriptionStatus.ACTIVE,
            SubscriptionStatus.GRACE_PERIOD,
            SubscriptionStatus.BILLING_RETRY,
            -> subscription.expiresAt == null || subscription.expiresAt!!.isAfter(now)
            SubscriptionStatus.CANCELLED,
            -> subscription.expiresAt != null && subscription.expiresAt!!.isAfter(now)
            else -> false
        }
        val event = SubscriptionChangedEvent(
            userId = userId,
            provider = provider,
            providerSubscriptionId = providerSubscriptionId,
            productId = subscription.productId,
            status = subscription.status,
            premiumActive = premiumActive,
            expiresAt = subscription.expiresAt,
        )
        try {
            kafkaTemplate.send("subscriptions", userId, event)
        } catch (ex: Exception) {
            log.warn("Failed to publish SubscriptionChangedEvent for userId={}", userId, ex)
        }
    }
}
