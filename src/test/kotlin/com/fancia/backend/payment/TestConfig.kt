package com.fancia.backend.payment

import com.fancia.backend.payment.core.support.stripe.StripeConnectClient
import org.mockito.Mockito.mock
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Primary
import org.springframework.security.oauth2.jwt.JwtDecoder
import org.springframework.test.context.DynamicPropertyRegistrar
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.kafka.KafkaContainer
import org.testcontainers.utility.DockerImageName
import org.wiremock.integrations.testcontainers.WireMockContainer

@TestConfiguration(proxyBeanMethods = false)
class TestConfig {
    @Bean
    @ServiceConnection
    fun postgres(): PostgreSQLContainer<*> {
        return PostgreSQLContainer(
            DockerImageName.parse("postgis/postgis:16-3.4-alpine")
                .asCompatibleSubstituteFor("postgres"),
        )
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test")
    }

    @Bean
    @ServiceConnection
    fun kafka(): KafkaContainer {
        return KafkaContainer("apache/kafka-native:3.8.0")
            .withEnv("KAFKA_AUTO_CREATE_TOPICS_ENABLE", "true")
    }

    @Bean
    fun wiremock(): WireMockContainer {
        return WireMockContainer("wiremock/wiremock:3.12.0").apply {
            start()
        }
    }

    @Bean
    fun wiremockProperties(wiremock: WireMockContainer): DynamicPropertyRegistrar {
        return DynamicPropertyRegistrar { registry ->
            registry.add("spring.cloud.openfeign.client.config.user-service.url") {
                wiremock.baseUrl
            }
            registry.add("spring.cloud.openfeign.client.config.user-internal-service.url") {
                wiremock.baseUrl
            }
        }
    }

    @Bean
    fun jwtDecoder(): JwtDecoder = mock()

    @Bean
    @Primary
    fun stripeConnectClient(): StripeConnectClient = mock(StripeConnectClient::class.java)

    @Bean
    fun testProperties(): DynamicPropertyRegistrar =
        DynamicPropertyRegistrar { registry ->
            registry.add("spring.jpa.hibernate.ddl-auto") { "none" }
            registry.add("spring.flyway.enabled") { "true" }
            registry.add("spring.flyway.placeholders.gis_admin_password") { "test-gis-admin" }
            registry.add("spring.cloud.aws.secretsmanager.enabled") { "false" }
            registry.add("spring.cloud.aws.region.static") { "us-east-1" }
            registry.add("spring.kafka.listener.auto-startup") { "false" }
            registry.add("spring.kafka.admin.auto-create") { "false" }
            registry.add("app.apple.verify-signature") { "false" }
            registry.add("app.apple.bundle-id") { "com.fancia.app" }
            registry.add("app.google.package-name") { "com.fancia.app" }
            registry.add("app.google.verify-pubsub-auth") { "false" }
            registry.add("app.stripe.secret-key") { "sk_test_dummy" }
            registry.add("app.stripe.connect.account-country") { "GB" }
            registry.add("app.base-url") { "http://localhost/payment/api" }
            registry.add("app.allowed-origins") { "https://fancia.co.uk" }
            registry.add("spring.autoconfigure.exclude") {
                listOf(
                    "io.awspring.cloud.autoconfigure.core.AwsAutoConfiguration",
                    "io.awspring.cloud.autoconfigure.core.CredentialsProviderAutoConfiguration",
                    "io.awspring.cloud.autoconfigure.secretsmanager.AwsSecretsManagerAutoConfiguration",
                ).joinToString(",")
            }
        }
}
