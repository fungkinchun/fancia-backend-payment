package com.fancia.backend.payment.core.support.stripe

import com.fancia.backend.shared.user.core.enums.SubscriptionStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import java.time.ZoneOffset

class StripeStatusMappingTest : FunSpec({
    val client = StripeClient(
        com.fancia.backend.payment.config.ApplicationProperties().apply {
            stripe.secretKey = "sk_test_x"
        },
    )

    test("maps active and trialing to ACTIVE") {
        client.mapStatus("active", null) shouldBe SubscriptionStatus.ACTIVE
        client.mapStatus("trialing", null) shouldBe SubscriptionStatus.ACTIVE
    }

    test("maps past_due to BILLING_RETRY") {
        client.mapStatus("past_due", null) shouldBe SubscriptionStatus.BILLING_RETRY
    }

    test("maps active with cancel_at_period_end to CANCELLED") {
        val future = LocalDateTime.now(ZoneOffset.UTC).plusDays(3)
        client.mapStatus("active", future, cancelAtPeriodEnd = true) shouldBe SubscriptionStatus.CANCELLED
    }

    test("maps canceled with future expiry to CANCELLED") {
        val future = LocalDateTime.now(ZoneOffset.UTC).plusDays(3)
        client.mapStatus("canceled", future) shouldBe SubscriptionStatus.CANCELLED
    }

    test("maps canceled with past expiry to EXPIRED") {
        val past = LocalDateTime.now(ZoneOffset.UTC).minusDays(1)
        client.mapStatus("canceled", past) shouldBe SubscriptionStatus.EXPIRED
    }
})
