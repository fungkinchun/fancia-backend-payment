package com.fancia.backend.payment.core.service

import com.fancia.backend.payment.core.repository.StripeConnectedAccountRepository
import com.fancia.backend.shared.payment.core.exception.ConnectAccountException
import org.springframework.stereotype.Component
import java.util.UUID

@Component
class StripeConnectedAccountLookup(
    private val connectAccountService: ConnectAccountService,
    private val stripeConnectedAccountRepository: StripeConnectedAccountRepository,
) {
    fun requirePayoutReadyAccountId(userId: UUID): String {
        val readiness = connectAccountService.payoutReadiness(userId)
        if (!readiness.payoutsReady) {
            throw ConnectAccountException(
                message = "Seller payouts are not ready",
                errorCode = "CONNECT_PAYOUTS_NOT_READY",
            )
        }
        return stripeConnectedAccountRepository.findByUserId(userId)
            .orElseThrow {
                ConnectAccountException(
                    message = "Seller has no Stripe Connect account",
                    errorCode = "CONNECT_ACCOUNT_MISSING",
                )
            }
            .providerId
            ?: throw ConnectAccountException(
                message = "Seller Stripe account id is missing",
                errorCode = "CONNECT_ACCOUNT_MISSING",
            )
    }
}
