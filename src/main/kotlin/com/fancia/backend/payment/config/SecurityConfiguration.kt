package com.fancia.backend.payment.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.Customizer
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver
import org.springframework.security.web.SecurityFilterChain

@EnableMethodSecurity
@EnableWebSecurity
@Configuration
class SecurityConfiguration {
    @Bean
    fun bearerTokenResolver(): BearerTokenResolver {
        val defaultResolver = DefaultBearerTokenResolver()
        return BearerTokenResolver { request ->
            val path = request.requestURI.removePrefix(request.contextPath ?: "")
            if (path.startsWith("/api/webhooks/")) {
                null
            } else {
                defaultResolver.resolve(request)
            }
        }
    }

    @Bean
    fun securityFilterChain(
        http: HttpSecurity,
        bearerTokenResolver: BearerTokenResolver,
    ): SecurityFilterChain {
        http.authorizeHttpRequests { customizer ->
            customizer.requestMatchers(HttpMethod.POST, "/api/webhooks/**").permitAll()
            customizer.requestMatchers("/api/connect", "/api/connect/**").authenticated()
            customizer.requestMatchers(HttpMethod.GET, "/internal/connect/accounts/*").permitAll()
            customizer.requestMatchers(HttpMethod.POST, "/internal/checkout/sessions").permitAll()
            customizer.requestMatchers(HttpMethod.POST, "/internal/checkout/refunds").permitAll()
            customizer.requestMatchers(HttpMethod.GET, "/api/**").permitAll()
            customizer.requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
            customizer.requestMatchers("/actuator/**").permitAll()
            customizer.requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
            customizer.anyRequest().authenticated()
        }.oauth2ResourceServer { oauth2ResourceServer ->
            oauth2ResourceServer.jwt(Customizer.withDefaults())
            oauth2ResourceServer.bearerTokenResolver(bearerTokenResolver)
        }.csrf { it.disable() }
        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
