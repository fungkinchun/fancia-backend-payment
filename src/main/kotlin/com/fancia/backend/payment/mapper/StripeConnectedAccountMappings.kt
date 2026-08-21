package com.fancia.backend.payment.mapper

import com.fancia.backend.payment.core.entity.StripeConnectedAccount
import com.fancia.backend.shared.payment.core.dto.ConnectAccountResponse
import com.fancia.backend.shared.payment.core.dto.PayoutReadinessResponse
import com.fancia.backend.shared.user.core.enums.ConnectedAccountProvider
import java.util.UUID

fun StripeConnectedAccount.payoutsReady(): Boolean = chargesEnabled && payoutsEnabled

fun StripeConnectedAccount.toDto(): ConnectAccountResponse =
    ConnectAccountResponse(
        userId = user!!.id!!,
        provider = provider,
        providerId = providerId,
        country = country,
        defaultCurrency = defaultCurrency,
        chargesEnabled = chargesEnabled,
        payoutsEnabled = payoutsEnabled,
        detailsSubmitted = detailsSubmitted,
        payoutsReady = payoutsReady(),
        disabledReason = disabledReason,
        connectedAt = connectedAt,
        onboardedAt = onboardedAt,
    )

fun StripeConnectedAccount.toPayoutReadiness(): PayoutReadinessResponse =
    PayoutReadinessResponse(
        userId = user!!.id!!,
        provider = provider,
        providerId = providerId,
        payoutsReady = payoutsReady(),
        chargesEnabled = chargesEnabled,
        payoutsEnabled = payoutsEnabled,
        detailsSubmitted = detailsSubmitted,
        defaultCurrency = defaultCurrency,
    )

fun notPayoutReady(userId: UUID): PayoutReadinessResponse =
    PayoutReadinessResponse(
        userId = userId,
        provider = ConnectedAccountProvider.STRIPE.value,
        providerId = null,
        payoutsReady = false,
        chargesEnabled = false,
        payoutsEnabled = false,
        detailsSubmitted = false,
        defaultCurrency = null,
    )
