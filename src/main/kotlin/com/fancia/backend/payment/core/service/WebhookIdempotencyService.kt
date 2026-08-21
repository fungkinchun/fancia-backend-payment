package com.fancia.backend.payment.core.service

import com.fancia.backend.payment.core.entity.WebhookEvent
import com.fancia.backend.payment.core.repository.WebhookEventRepository
import com.fancia.backend.shared.user.core.enums.PaymentProvider
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime

@Service
class WebhookIdempotencyService(
    private val webhookEventRepository: WebhookEventRepository,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun tryClaim(
        provider: PaymentProvider,
        eventId: String,
        eventType: String?,
        rawPayload: String?,
    ): Boolean {
        if (webhookEventRepository.existsByProviderAndEventId(provider, eventId)) {
            log.info("Skipping duplicate webhook provider={} eventId={}", provider, eventId)
            return false
        }

        return try {
            webhookEventRepository.save(
                WebhookEvent().apply {
                    this.provider = provider
                    this.eventId = eventId
                    this.eventType = eventType
                    this.rawPayload = rawPayload
                    this.processedAt = LocalDateTime.now()
                },
            )
            true
        } catch (_: DataIntegrityViolationException) {
            log.info("Race on webhook claim provider={} eventId={}", provider, eventId)
            false
        }
    }
}
