package com.fancia.backend.payment.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "app")
class ApplicationProperties {
    var allowedOrigins: List<String> = emptyList()
    var applicationName: String? = null
    var baseUrl: String? = null
    var loginPageUrl: String? = null
    val apple = AppleProperties()
    val google = GoogleProperties()
    val stripe = StripeProperties()

    class AppleProperties {
        var bundleId: String? = null
        var verifySignature: Boolean = true
        var allowedEnvironments: List<String> = listOf("Production", "Sandbox")
    }

    class GoogleProperties {
        var packageName: String? = null
        var serviceAccountJson: String? = null
        var verifyPubsubAuth: Boolean = true
        var pubsubAudience: String? = null
        var pubsubServiceAccountEmail: String? = null
    }

    class StripeProperties {
        var webhookSecret: String? = null
        var secretKey: String? = null
        var verifySignature: Boolean = true
        var priceId: String? = null
        var trialPeriodDays: Long = 30
        val connect = ConnectProperties()

        class ConnectProperties {
            var accountCountry: String = "GB"
            var platformFeePercent: Double = 5.0
            var premiumPlatformFeePercent: Double = 2.5
        }
    }
}
