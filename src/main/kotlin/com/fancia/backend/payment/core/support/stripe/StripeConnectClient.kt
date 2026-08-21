package com.fancia.backend.payment.core.support.stripe

import com.fancia.backend.payment.config.ApplicationProperties
import com.fancia.backend.shared.payment.core.exception.ConnectAccountException
import com.fancia.backend.shared.user.core.exception.InvalidStripeNotificationException
import com.stripe.Stripe
import com.stripe.model.Account
import com.stripe.model.AccountLink
import com.stripe.model.LoginLink
import com.stripe.net.RequestOptions
import com.stripe.param.AccountCreateParams
import com.stripe.param.AccountLinkCreateParams
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

data class StripeConnectAccountSnapshot(
    val accountId: String,
    val chargesEnabled: Boolean,
    val payoutsEnabled: Boolean,
    val detailsSubmitted: Boolean,
    val country: String?,
    val defaultCurrency: String?,
    val disabledReason: String?,
    val userIdMetadata: String?,
    val rawJson: String?,
)

data class StripeHostedLink(
    val url: String,
    val expiresAt: LocalDateTime?,
)

@Component
class StripeConnectClient(
    private val applicationProperties: ApplicationProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun createExpressAccount(userId: UUID): StripeConnectAccountSnapshot {
        val params = AccountCreateParams.builder()
            .setType(AccountCreateParams.Type.EXPRESS)
            .setCountry(applicationProperties.stripe.connect.accountCountry)
            .setCapabilities(
                AccountCreateParams.Capabilities.builder()
                    .setTransfers(
                        AccountCreateParams.Capabilities.Transfers.builder()
                            .setRequested(true)
                            .build(),
                    )
                    .build(),
            )
            .setSettings(
                AccountCreateParams.Settings.builder()
                    .setPayouts(
                        AccountCreateParams.Settings.Payouts.builder()
                            .setSchedule(
                                AccountCreateParams.Settings.Payouts.Schedule.builder()
                                    .setInterval(
                                        AccountCreateParams.Settings.Payouts.Schedule.Interval.MANUAL,
                                    )
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .putMetadata("userId", userId.toString())
            .build()

        return try {
            toSnapshot(Account.create(params, requestOptions()))
        } catch (ex: Exception) {
            log.warn("Stripe Account.create failed userId={}", userId, ex)
            throw ConnectAccountException(
                message = "Could not create a Stripe payout account: ${ex.message}",
            )
        }
    }

    fun getAccount(accountId: String): StripeConnectAccountSnapshot =
        try {
            toSnapshot(Account.retrieve(accountId, requestOptions()))
        } catch (ex: Exception) {
            log.warn("Stripe Account.retrieve failed accountId={}", accountId, ex)
            throw ConnectAccountException(
                message = "Could not read the Stripe payout account: ${ex.message}",
            )
        }

    fun createOnboardingLink(
        accountId: String,
        refreshUrl: String,
        returnUrl: String,
    ): StripeHostedLink {
        val params = AccountLinkCreateParams.builder()
            .setAccount(accountId)
            .setRefreshUrl(refreshUrl)
            .setReturnUrl(returnUrl)
            .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
            .build()

        return try {
            val link = AccountLink.create(params, requestOptions())
            val url = link.url
                ?: throw ConnectAccountException(message = "Stripe onboarding link is missing a url")
            StripeHostedLink(url = url, expiresAt = link.expiresAt.toLocalDateTime())
        } catch (ex: ConnectAccountException) {
            throw ex
        } catch (ex: Exception) {
            log.warn("Stripe AccountLink.create failed accountId={}", accountId, ex)
            throw ConnectAccountException(
                message = "Could not start Stripe onboarding: ${ex.message}",
            )
        }
    }

    fun createDashboardLink(accountId: String): StripeHostedLink =
        try {
            val link = LoginLink.createOnAccount(accountId, requestOptions())
            val url = link.url
                ?: throw ConnectAccountException(message = "Stripe dashboard link is missing a url")
            StripeHostedLink(url = url, expiresAt = null)
        } catch (ex: ConnectAccountException) {
            throw ex
        } catch (ex: Exception) {
            log.warn("Stripe LoginLink.createOnAccount failed accountId={}", accountId, ex)
            throw ConnectAccountException(
                message = "Could not open the Stripe dashboard: ${ex.message}",
            )
        }

    fun toSnapshot(account: Account): StripeConnectAccountSnapshot =
        StripeConnectAccountSnapshot(
            accountId = account.id,
            chargesEnabled = account.chargesEnabled == true,
            payoutsEnabled = account.payoutsEnabled == true,
            detailsSubmitted = account.detailsSubmitted == true,
            country = account.country,
            defaultCurrency = account.defaultCurrency,
            disabledReason = account.requirements?.disabledReason,
            userIdMetadata = account.metadata?.get("userId"),
            rawJson = account.toJson(),
        )

    private fun Long?.toLocalDateTime(): LocalDateTime? =
        this?.takeIf { it > 0 }
            ?.let { LocalDateTime.ofInstant(Instant.ofEpochSecond(it), ZoneOffset.UTC) }

    private fun requestOptions(): RequestOptions {
        val key = applicationProperties.stripe.secretKey
            ?.takeIf { it.isNotBlank() }
            ?: throw InvalidStripeNotificationException(message = "app.stripe.secret-key is not configured")
        Stripe.apiKey = key
        return RequestOptions.builder().setApiKey(key).build()
    }
}
