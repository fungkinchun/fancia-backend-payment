package com.fancia.backend.payment.core.service

import com.fancia.backend.payment.core.entity.StripeConnectedAccount
import com.fancia.backend.payment.core.repository.StripeConnectedAccountRepository
import com.fancia.backend.payment.core.support.stripe.StripeConnectAccountSnapshot
import com.fancia.backend.payment.core.support.stripe.StripeConnectClient
import com.fancia.backend.payment.mapper.notPayoutReady
import com.fancia.backend.payment.mapper.toDto
import com.fancia.backend.payment.mapper.toPayoutReadiness
import com.fancia.backend.shared.payment.core.dto.ConnectAccountResponse
import com.fancia.backend.shared.payment.core.dto.ConnectLinkResponse
import com.fancia.backend.shared.payment.core.dto.ConnectOnboardingRequest
import com.fancia.backend.shared.payment.core.dto.PayoutReadinessResponse
import com.fancia.backend.shared.payment.core.exception.ConnectAccountException
import com.fancia.backend.shared.payment.core.exception.ConnectAccountNotFoundException
import com.fancia.backend.shared.user.core.entity.User
import jakarta.persistence.EntityManager
import org.slf4j.LoggerFactory
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.UUID

@Service
class ConnectAccountService(
    private val stripeConnectedAccountRepository: StripeConnectedAccountRepository,
    private val stripeConnectClient: StripeConnectClient,
    private val entityManager: EntityManager,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun startOnboarding(userId: UUID, request: ConnectOnboardingRequest): ConnectLinkResponse {
        val account = ensureAccount(userId)
        val link = stripeConnectClient.createOnboardingLink(
            accountId = account.providerId!!,
            refreshUrl = request.refreshUrl,
            returnUrl = request.returnUrl,
        )
        return ConnectLinkResponse(url = link.url, expiresAt = link.expiresAt)
    }

    @Transactional(readOnly = true)
    fun status(userId: UUID): ConnectAccountResponse =
        stripeConnectedAccountRepository.findByUserId(userId)
            .orElseThrow { ConnectAccountNotFoundException() }
            .toDto()

    @Transactional
    fun refreshStatus(userId: UUID): ConnectAccountResponse {
        val account = stripeConnectedAccountRepository.findByUserId(userId)
            .orElseThrow { ConnectAccountNotFoundException() }
        val snapshot = stripeConnectClient.getAccount(account.providerId!!)
        return stripeConnectedAccountRepository.save(account.updateFrom(snapshot)).toDto()
    }

    @Transactional(readOnly = true)
    fun payoutReadiness(userId: UUID): PayoutReadinessResponse =
        stripeConnectedAccountRepository.findByUserId(userId)
            .map { it.toPayoutReadiness() }
            .orElseGet { notPayoutReady(userId) }

    @Transactional(readOnly = true)
    fun dashboardLink(userId: UUID): ConnectLinkResponse {
        val account = stripeConnectedAccountRepository.findByUserId(userId)
            .orElseThrow { ConnectAccountNotFoundException() }
        if (!account.detailsSubmitted) {
            throw ConnectAccountException(
                message = "Finish Stripe onboarding before opening the payouts dashboard",
                errorCode = "CONNECT_ONBOARDING_INCOMPLETE",
            )
        }
        val link = stripeConnectClient.createDashboardLink(account.providerId!!)
        return ConnectLinkResponse(url = link.url, expiresAt = link.expiresAt)
    }

    @Transactional
    fun applySnapshot(snapshot: StripeConnectAccountSnapshot) {
        val account = stripeConnectedAccountRepository.findByProviderId(snapshot.accountId)
            .orElse(null)
            ?: snapshot.userIdMetadata
                ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                ?.let { stripeConnectedAccountRepository.findByUserId(it).orElse(null) }

        if (account == null) {
            log.info("Ignoring Stripe account update for unknown account={}", snapshot.accountId)
            return
        }

        stripeConnectedAccountRepository.save(account.updateFrom(snapshot))
        log.info(
            "Stripe connect account={} userId={} charges={} payouts={} detailsSubmitted={}",
            snapshot.accountId,
            account.user?.id,
            snapshot.chargesEnabled,
            snapshot.payoutsEnabled,
            snapshot.detailsSubmitted,
        )
    }

    private fun ensureAccount(userId: UUID): StripeConnectedAccount {
        val existing = stripeConnectedAccountRepository.findByUserId(userId).orElse(null)
        if (existing != null) {
            return existing
        }

        val snapshot = stripeConnectClient.createExpressAccount(userId)
        val account = StripeConnectedAccount(
            providerId = snapshot.accountId,
            user = entityManager.getReference(User::class.java, userId),
        ).also { it.createdBy = userId }

        return try {
            stripeConnectedAccountRepository.saveAndFlush(account.updateFrom(snapshot))
        } catch (ex: DataIntegrityViolationException) {
            log.warn(
                "Concurrent Stripe connect account creation for userId={}; account={} is now orphaned",
                userId,
                snapshot.accountId,
                ex,
            )
            throw ConnectAccountException(
                message = "Payout account setup is already in progress, please try again",
                errorCode = "CONNECT_ACCOUNT_SETUP_IN_PROGRESS",
            )
        }
    }

    private fun StripeConnectedAccount.updateFrom(snapshot: StripeConnectAccountSnapshot): StripeConnectedAccount = also {
        it.country = snapshot.country
        it.defaultCurrency = snapshot.defaultCurrency
        it.chargesEnabled = snapshot.chargesEnabled
        it.payoutsEnabled = snapshot.payoutsEnabled
        it.detailsSubmitted = snapshot.detailsSubmitted
        it.disabledReason = snapshot.disabledReason
        it.rawPayload = snapshot.rawJson
        if (snapshot.detailsSubmitted && it.onboardedAt == null) {
            it.onboardedAt = LocalDateTime.now()
        }
    }
}
