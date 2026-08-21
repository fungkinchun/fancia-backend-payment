package com.fancia.backend.payment.core.support.apple

import com.fancia.backend.payment.config.ApplicationProperties
import com.fancia.backend.shared.user.core.exception.InvalidAppleNotificationException
import com.nimbusds.jose.JWSAlgorithm
import com.nimbusds.jose.crypto.ECDSAVerifier
import com.nimbusds.jose.util.X509CertUtils
import com.nimbusds.jwt.SignedJWT
import org.slf4j.LoggerFactory
import org.springframework.core.io.ClassPathResource
import org.springframework.stereotype.Component
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.security.interfaces.ECPublicKey
import java.util.Date

@Component
class AppleJwsVerifier(
    private val applicationProperties: ApplicationProperties,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    private val appleRootCa: X509Certificate by lazy {
        val resource = ClassPathResource("apple/AppleRootCA-G3.cer")
        if (!resource.exists()) {
            error("Missing classpath resource apple/AppleRootCA-G3.cer")
        }
        resource.inputStream.use { input ->
            CertificateFactory.getInstance("X.509").generateCertificate(input) as X509Certificate
        }
    }

    fun verifyAndDecodeNotification(signedPayload: String): AppleNotification {
        val payloadJson = verifyAndDecodeJws(signedPayload)
        verifyNestedJwts(payloadJson)
        val notification = AppleNotificationParser.parseNotificationPayload(payloadJson)
        validateBundleAndEnvironment(notification)
        return notification
    }

    fun verifyAndDecodeJws(jws: String): String {
        if (!applicationProperties.apple.verifySignature) {
            log.warn("Apple JWS signature verification is DISABLED (app.apple.verify-signature=false)")
            return AppleNotificationParser.decodeJwsPayload(jws)
        }

        return try {
            val signedJwt = SignedJWT.parse(jws)
            if (signedJwt.header.algorithm != JWSAlgorithm.ES256) {
                throw InvalidAppleNotificationException(message = "Unexpected JWS algorithm: ${signedJwt.header.algorithm}")
            }

            val x5c = signedJwt.header.x509CertChain
                ?: throw InvalidAppleNotificationException(message = "Missing x5c certificate chain")
            if (x5c.isEmpty()) {
                throw InvalidAppleNotificationException(message = "Empty x5c certificate chain")
            }

            val chain = x5c.map { X509CertUtils.parse(it.decode()) }
            validateCertificateChain(chain)

            val leaf = chain.first()
            val verifier = ECDSAVerifier(leaf.publicKey as ECPublicKey)
            if (!signedJwt.verify(verifier)) {
                throw InvalidAppleNotificationException(message = "Apple JWS signature verification failed")
            }

            signedJwt.payload.toString()
        } catch (ex: InvalidAppleNotificationException) {
            throw ex
        } catch (ex: Exception) {
            log.warn("Failed to verify Apple JWS", ex)
            throw InvalidAppleNotificationException(message = "Apple JWS verification failed: ${ex.message}")
        }
    }

    private fun verifyNestedJwts(outerPayloadJson: String) {
        val root = com.fasterxml.jackson.module.kotlin.jacksonObjectMapper().readTree(outerPayloadJson)
        val data = root.path("data")
        data.path("signedTransactionInfo").asText(null)?.let { verifyAndDecodeJws(it) }
        data.path("signedRenewalInfo").asText(null)?.let { verifyAndDecodeJws(it) }
    }

    private fun validateCertificateChain(chain: List<X509Certificate>) {
        val now = Date()
        chain.forEach { cert ->
            try {
                cert.checkValidity(now)
            } catch (ex: Exception) {
                throw InvalidAppleNotificationException(message = "Apple certificate not valid: ${ex.message}")
            }
        }

        val cf = CertificateFactory.getInstance("X.509")
        val certPath = cf.generateCertPath(chain)
        val trustAnchors = setOf(TrustAnchor(appleRootCa, null))
        val params = PKIXParameters(trustAnchors).apply {
            isRevocationEnabled = false
        }
        try {
            CertPathValidator.getInstance("PKIX").validate(certPath, params)
        } catch (ex: Exception) {
            throw InvalidAppleNotificationException(
                message = "Apple certificate chain validation failed: ${ex.message}",
            )
        }
    }

    private fun validateBundleAndEnvironment(notification: AppleNotification) {
        val expectedBundle = applicationProperties.apple.bundleId
        if (!expectedBundle.isNullOrBlank() &&
            !notification.bundleId.isNullOrBlank() &&
            notification.bundleId != expectedBundle
        ) {
            throw InvalidAppleNotificationException(
                message = "Unexpected Apple bundleId: ${notification.bundleId}",
            )
        }

        val env = notification.environment ?: notification.transaction?.environment
        val allowed = applicationProperties.apple.allowedEnvironments
        if (!env.isNullOrBlank() && allowed.isNotEmpty() && env !in allowed) {
            throw InvalidAppleNotificationException(message = "Unexpected Apple environment: $env")
        }
    }
}
