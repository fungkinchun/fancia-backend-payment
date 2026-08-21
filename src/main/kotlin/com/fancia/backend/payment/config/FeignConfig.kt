package com.fancia.backend.payment.config

import feign.RequestInterceptor
import feign.RequestTemplate
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken
import org.springframework.stereotype.Component

@EnableMethodSecurity
@EnableWebSecurity
@Configuration
class FeignConfig {
    @Component
    class FeignClientInterceptor : RequestInterceptor {
        override fun apply(template: RequestTemplate) {
            val authentication = SecurityContextHolder.getContext().authentication
            if (authentication != null && authentication is JwtAuthenticationToken) {
                val tokenValue = authentication.token.tokenValue
                template.header("Authorization", "Bearer $tokenValue")
            }
        }
    }
}
