package com.fancia.backend.payment.core.support.google

import com.fancia.backend.payment.config.ApplicationProperties
import com.fancia.backend.shared.user.core.exception.InvalidGoogleNotificationException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.RSASSAVerifier
import com.nimbusds.jose.jwk.JWKSet
import com.nimbusds.jwt.SignedJWT
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.security.interfaces.RSAPublicKey
import java.time.Instant
import java.util.concurrent.atomic.AtomicReference

@Component
class GooglePubSubOidcVerifier(
    private val applicationProperties: ApplicationProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val cachedJwks = AtomicReference<Pair<Instant, JWKSet>?>(null)

    fun verifyAuthorizationHeader(authorizationHeader: String?) {
        if (!applicationProperties.google.verifyPubsubAuth) {
            log.warn("Google Pub/Sub OIDC verification is DISABLED (app.google.verify-pubsub-auth=false)")
            return
        }

        val token = authorizationHeader
            ?.takeIf { it.startsWith("Bearer ", ignoreCase = true) }
            ?.substringAfter(' ')
            ?.trim()
            ?: throw InvalidGoogleNotificationException(message = "Missing Pub/Sub Bearer token")

        try {
            val jwt = SignedJWT.parse(token)
            if (jwt.header.algorithm != JWSAlgorithm.RS256) {
                throw InvalidGoogleNotificationException(message = "Unexpected Pub/Sub JWT alg: ${jwt.header.algorithm}")
            }

            val kid = jwt.header.keyID
                ?: throw InvalidGoogleNotificationException(message = "Pub/Sub JWT missing kid")
            val jwk = loadJwks().getKeyByKeyId(kid)
                ?: throw InvalidGoogleNotificationException(message = "No Google JWK for kid=$kid")
            val publicKey = jwk.toRSAKey().toRSAPublicKey()
            if (!jwt.verify(RSASSAVerifier(publicKey as RSAPublicKey))) {
                throw InvalidGoogleNotificationException(message = "Pub/Sub JWT signature invalid")
            }

            val claims = jwt.jwtClaimsSet
            val now = Instant.now()
            if (claims.expirationTime == null || claims.expirationTime.toInstant().isBefore(now)) {
                throw InvalidGoogleNotificationException(message = "Pub/Sub JWT expired")
            }

            val issuer = claims.issuer
            if (issuer != "https://accounts.google.com" && issuer != "accounts.google.com") {
                throw InvalidGoogleNotificationException(message = "Unexpected Pub/Sub JWT iss: $issuer")
            }

            val expectedAud = applicationProperties.google.pubsubAudience
            if (!expectedAud.isNullOrBlank()) {
                val audiences = claims.audience ?: emptyList()
                if (expectedAud !in audiences) {
                    throw InvalidGoogleNotificationException(
                        message = "Pub/Sub JWT aud mismatch; expected=$expectedAud actual=$audiences",
                    )
                }
            }

            val expectedEmail = applicationProperties.google.pubsubServiceAccountEmail
            if (!expectedEmail.isNullOrBlank()) {
                val email = claims.getStringClaim("email")
                if (email != expectedEmail) {
                    throw InvalidGoogleNotificationException(
                        message = "Pub/Sub JWT email mismatch; expected=$expectedEmail actual=$email",
                    )
                }
            }
        } catch (ex: InvalidGoogleNotificationException) {
            throw ex
        } catch (ex: Exception) {
            log.warn("Failed to verify Google Pub/Sub OIDC token", ex)
            throw InvalidGoogleNotificationException(message = "Pub/Sub OIDC verification failed: ${ex.message}")
        }
    }

    private fun loadJwks(): JWKSet {
        val cached = cachedJwks.get()
        if (cached != null && cached.first.plusSeconds(3600).isAfter(Instant.now())) {
            return cached.second
        }
        val jwks = JWKSet.load(URI("https://www.googleapis.com/oauth2/v3/certs").toURL())
        cachedJwks.set(Instant.now() to jwks)
        return jwks
    }
}
