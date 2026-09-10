package com.fancia.backend.payment

import com.fancia.backend.payment.core.entity.Subscription
import com.fancia.backend.payment.core.repository.SubscriptionRepository
import com.fancia.backend.shared.user.core.enums.PaymentProvider
import com.fancia.backend.shared.user.core.enums.SubscriptionStatus
import com.github.tomakehurst.wiremock.client.WireMock.aResponse
import com.github.tomakehurst.wiremock.client.WireMock.configureFor
import com.github.tomakehurst.wiremock.client.WireMock.put
import com.github.tomakehurst.wiremock.client.WireMock.putRequestedFor
import com.github.tomakehurst.wiremock.client.WireMock.reset as wireMockReset
import com.github.tomakehurst.wiremock.client.WireMock.stubFor
import com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching
import com.github.tomakehurst.wiremock.client.WireMock.verify
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import jakarta.persistence.EntityManager
import org.hamcrest.CoreMatchers.`is`
import org.hamcrest.CoreMatchers.notNullValue
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc
import org.springframework.context.annotation.Import
import org.springframework.http.MediaType.APPLICATION_JSON
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.post
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.junit.jupiter.Testcontainers
import org.wiremock.integrations.testcontainers.WireMockContainer
import tools.jackson.databind.json.JsonMapper
import java.time.LocalDateTime
import java.time.temporal.ChronoUnit
import java.util.UUID

@SpringBootTest(classes = [PaymentApplication::class])
@AutoConfigureMockMvc
@Testcontainers
@Import(TestConfig::class)
class SubscriptionReferralIntegrationTest(
    private val mockMvc: MockMvc,
    private val jsonMapper: JsonMapper,
    private val wiremock: WireMockContainer,
    private val subscriptionRepository: SubscriptionRepository,
    private val entityManager: EntityManager,
    private val transactionTemplate: TransactionTemplate,
) : FunSpec({
    beforeSpec {
        configureFor(wiremock.host, wiremock.getMappedPort(8080))
    }

    beforeEach {
        wireMockReset()
        transactionTemplate.executeWithoutResult {
            subscriptionRepository.deleteAll()
        }
    }

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

    fun stubUserPremiumUpdate(userId: UUID) {
        stubFor(
            put(urlPathMatching("/internal/v1/users/.*/premium"))
                .willReturn(
                    aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                            jsonMapper.writeValueAsString(
                                mapOf(
                                    "id" to userId.toString(),
                                    "premiumActive" to true,
                                ),
                            ),
                        ),
                ),
        )
    }

    test("should grant complimentary referral premium") {
        val userId = UUID.randomUUID()
        seedUser(userId)
        stubUserPremiumUpdate(userId)

        val before = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)
        mockMvc.post("/internal/v1/subscriptions/referral") {
            content = jsonMapper.writeValueAsString(
                mapOf(
                    "userId" to userId.toString(),
                    "days" to 30,
                ),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.userId", `is`(userId.toString()))
            jsonPath("$.premiumActive", `is`(true))
            jsonPath("$.premiumExpiresAt", `is`(notNullValue()))
        }
        val after = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)

        val saved = subscriptionRepository
            .findByProviderAndProviderSubscriptionId(PaymentProvider.REFERRAL, "referral:$userId")
            .orElseThrow()
        saved.status shouldBe SubscriptionStatus.ACTIVE
        saved.productId shouldBe "referral_month"
        saved.expiresAt!!.isAfter(before.plusDays(29)) shouldBe true
        saved.expiresAt!!.isBefore(after.plusDays(31)) shouldBe true
        verify(putRequestedFor(urlPathMatching("/internal/v1/users/.*/premium")))
    }

    test("should stack referral premium onto existing entitlement") {
        val userId = UUID.randomUUID()
        seedUser(userId)
        stubUserPremiumUpdate(userId)

        val currentExpiry = LocalDateTime.now().plusDays(10).truncatedTo(ChronoUnit.SECONDS)
        transactionTemplate.executeWithoutResult {
            subscriptionRepository.save(
                Subscription().apply {
                    this.userId = userId
                    provider = PaymentProvider.STRIPE
                    providerSubscriptionId = "sub_existing"
                    productId = "premium_month"
                    status = SubscriptionStatus.ACTIVE
                    expiresAt = currentExpiry
                    createdBy = userId
                },
            )
        }

        mockMvc.post("/internal/v1/subscriptions/referral") {
            content = jsonMapper.writeValueAsString(
                mapOf(
                    "userId" to userId.toString(),
                    "days" to 30,
                ),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.premiumActive", `is`(true))
            jsonPath("$.premiumExpiresAt", `is`(notNullValue()))
        }

        val referral = subscriptionRepository
            .findByProviderAndProviderSubscriptionId(PaymentProvider.REFERRAL, "referral:$userId")
            .orElseThrow()
        referral.expiresAt shouldBe currentExpiry.plusDays(30)
    }

    test("should extend an existing referral subscription") {
        val userId = UUID.randomUUID()
        seedUser(userId)
        stubUserPremiumUpdate(userId)

        val originalExpiry = LocalDateTime.now().plusDays(5).truncatedTo(ChronoUnit.SECONDS)
        transactionTemplate.executeWithoutResult {
            subscriptionRepository.save(
                Subscription().apply {
                    this.userId = userId
                    provider = PaymentProvider.REFERRAL
                    providerSubscriptionId = "referral:$userId"
                    productId = "referral_month"
                    status = SubscriptionStatus.ACTIVE
                    expiresAt = originalExpiry
                    createdBy = userId
                },
            )
        }

        mockMvc.post("/internal/v1/subscriptions/referral") {
            content = jsonMapper.writeValueAsString(
                mapOf(
                    "userId" to userId.toString(),
                    "days" to 30,
                ),
            )
            contentType = APPLICATION_JSON
            accept = APPLICATION_JSON
        }.andExpect {
            status { isOk() }
            jsonPath("$.premiumActive", `is`(true))
            jsonPath("$.premiumExpiresAt", `is`(notNullValue()))
        }

        subscriptionRepository.findByUserId(userId).size shouldBe 1
        subscriptionRepository
            .findByProviderAndProviderSubscriptionId(PaymentProvider.REFERRAL, "referral:$userId")
            .orElseThrow()
            .expiresAt shouldBe originalExpiry.plusDays(30)
    }

    afterSpec {
        transactionTemplate.executeWithoutResult {
            subscriptionRepository.deleteAll()
        }
    }
})
