package com.fancia.backend.payment

import com.fancia.backend.payment.core.repository.StripeConnectedAccountRepository
import com.fancia.backend.payment.core.support.stripe.StripeConnectAccountSnapshot
import com.fancia.backend.payment.core.support.stripe.StripeConnectClient
import com.fancia.backend.payment.core.support.stripe.StripeHostedLink
import com.github.tomakehurst.wiremock.client.WireMock.configureFor
import com.github.tomakehurst.wiremock.client.WireMock.reset as wireMockReset
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.mockito.Mockito.reset as mockitoReset
import org.mockito.Mockito.`when`
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.get
import org.springframework.test.web.servlet.post
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Testcontainers
import org.wiremock.integrations.testcontainers.WireMockContainer
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.util.UUID

@SpringBootTest(classes = [PaymentApplication::class])
@AutoConfigureMockMvc
@Testcontainers
@Import(TestConfig::class)
class ConnectAccountControllerIntegrationTest(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
    private val wiremock: WireMockContainer,
    private val stripeConnectClient: StripeConnectClient,
    private val stripeConnectedAccountRepository: StripeConnectedAccountRepository,
    private val entityManager: EntityManager,
    private val transactionTemplate: TransactionTemplate,
) : FunSpec({
    beforeSpec {
        configureFor(wiremock.host, wiremock.getMappedPort(8080))
    }

    beforeEach {
        wireMockReset()
        mockitoReset(stripeConnectClient)
        transactionTemplate.executeWithoutResult {
            stripeConnectedAccountRepository.deleteAll()
        }
    }

    fun jwtFor(userId: UUID) = jwt().jwt { it.claim("userId", userId) }

    fun seedUser(userId: UUID) {
        transactionTemplate.executeWithoutResult {
            entityManager.createNativeQuery(
                """
                insert into "users" (
                    id, deleted, status, email, role, premium_active, visibility
                ) values (
                    :id, false, 'ACTIVE', :email, 'USER', false, 'PUBLIC'
                )
                """.trimIndent(),
            )
                .setParameter("id", userId)
                .setParameter("email", "$userId@example.com")
                .executeUpdate()
        }
    }

    fun snapshot(
        accountId: String = "acct_test_123",
        userId: UUID,
        ready: Boolean = false,
    ) = StripeConnectAccountSnapshot(
        accountId = accountId,
        chargesEnabled = ready,
        payoutsEnabled = ready,
        detailsSubmitted = ready,
        country = "GB",
        defaultCurrency = "gbp",
        disabledReason = null,
        userIdMetadata = userId.toString(),
        rawJson = """{"id":"$accountId"}""",
    )

    test("internal payout readiness is false when user has no connect account") {
        val userId = UUID.randomUUID()
        mockMvc.get("/internal/connect/accounts/{userId}", userId) {
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.userId", `is`(userId.toString()))
            jsonPath("$.payoutsReady", `is`(false))
            jsonPath("$.chargesEnabled", `is`(false))
        }
    }

    test("should start onboarding and return connect link") {
        val userId = UUID.randomUUID()
        seedUser(userId)
        `when`(stripeConnectClient.createExpressAccount(userId)).thenReturn(snapshot(userId = userId))
        `when`(
            stripeConnectClient.createOnboardingLink(
                accountId = "acct_test_123",
                refreshUrl = "https://fancia.co.uk/connect/refresh",
                returnUrl = "https://fancia.co.uk/connect/return",
            ),
        ).thenReturn(
            StripeHostedLink(
                url = "https://connect.stripe.com/setup/test",
                expiresAt = LocalDateTime.parse("2030-01-01T00:00:00"),
            ),
        )

        mockMvc.post("/api/connect/accounts/onboarding") {
            with(jwtFor(userId))
            content = jsonMapper.writeValueAsString(
                mapOf(
                    "returnUrl" to "https://fancia.co.uk/connect/return",
                    "refreshUrl" to "https://fancia.co.uk/connect/refresh",
                ),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.url", `is`("https://connect.stripe.com/setup/test"))
            jsonPath("$.expiresAt", `is`(notNullValue()))
        }

        stripeConnectedAccountRepository.findByUserId(userId).isPresent shouldBe true
    }

    test("should return me status and refresh from stripe") {
        val userId = UUID.randomUUID()
        seedUser(userId)
        `when`(stripeConnectClient.createExpressAccount(userId)).thenReturn(snapshot(userId = userId))
        `when`(
            stripeConnectClient.createOnboardingLink(
                accountId = "acct_test_123",
                refreshUrl = "https://fancia.co.uk/connect/refresh",
                returnUrl = "https://fancia.co.uk/connect/return",
            ),
        ).thenReturn(StripeHostedLink(url = "https://connect.stripe.com/setup/test", expiresAt = null))

        mockMvc.post("/api/connect/accounts/onboarding") {
            with(jwtFor(userId))
            content = jsonMapper.writeValueAsString(
                mapOf(
                    "returnUrl" to "https://fancia.co.uk/connect/return",
                    "refreshUrl" to "https://fancia.co.uk/connect/refresh",
                ),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect { status { isOk() } }

        mockMvc.get("/api/connect/accounts/me") {
            with(jwtFor(userId))
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.userId", `is`(userId.toString()))
            jsonPath("$.providerId", `is`("acct_test_123"))
            jsonPath("$.payoutsReady", `is`(false))
        }

        `when`(stripeConnectClient.getAccount("acct_test_123")).thenReturn(
            snapshot(userId = userId, ready = true),
        )

        mockMvc.post("/api/connect/accounts/me/refresh") {
            with(jwtFor(userId))
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.payoutsReady", `is`(true))
            jsonPath("$.chargesEnabled", `is`(true))
            jsonPath("$.payoutsEnabled", `is`(true))
            jsonPath("$.detailsSubmitted", `is`(true))
        }

        mockMvc.get("/internal/connect/accounts/{userId}", userId) {
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.payoutsReady", `is`(true))
            jsonPath("$.providerId", `is`("acct_test_123"))
        }
    }

    test("dashboard requires completed onboarding") {
        val userId = UUID.randomUUID()
        seedUser(userId)
        `when`(stripeConnectClient.createExpressAccount(userId)).thenReturn(snapshot(userId = userId))
        `when`(
            stripeConnectClient.createOnboardingLink(
                accountId = "acct_test_123",
                refreshUrl = "https://fancia.co.uk/connect/refresh",
                returnUrl = "https://fancia.co.uk/connect/return",
            ),
        ).thenReturn(StripeHostedLink(url = "https://connect.stripe.com/setup/test", expiresAt = null))

        mockMvc.post("/api/connect/accounts/onboarding") {
            with(jwtFor(userId))
            content = jsonMapper.writeValueAsString(
                mapOf(
                    "returnUrl" to "https://fancia.co.uk/connect/return",
                    "refreshUrl" to "https://fancia.co.uk/connect/refresh",
                ),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect { status { isOk() } }

        mockMvc.post("/api/connect/accounts/me/dashboard") {
            with(jwtFor(userId))
            accept = APPLICATION_JSON
        }.andExpect {
            status { isBadRequest() }
            jsonPath("$.errorCode", `is`("CONNECT_ONBOARDING_INCOMPLETE"))
        }
    }

    afterSpec {
        transactionTemplate.executeWithoutResult {
            stripeConnectedAccountRepository.deleteAll()
        }
    }
})
