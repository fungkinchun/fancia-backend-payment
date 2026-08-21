package com.fancia.backend.payment.core.support.apple

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.Base64

class AppleNotificationParserTest : FunSpec({
    test("decodes notification payload and nested transaction fields") {
        val transactionJson = """
            {
              "originalTransactionId": "1000000123456789",
              "transactionId": "1000000123456790",
              "productId": "com.fancia.premium.monthly",
              "expiresDate": 1893456000000,
              "appAccountToken": "11111111-1111-1111-1111-111111111111",
              "environment": "Sandbox"
            }
        """.trimIndent()
        val signedTransactionInfo = fakeJws(transactionJson)

        val notificationJson = """
            {
              "notificationType": "DID_RENEW",
              "subtype": null,
              "notificationUUID": "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee",
              "data": {
                "environment": "Sandbox",
                "bundleId": "com.fancia.app",
                "signedTransactionInfo": "$signedTransactionInfo"
              },
              "version": "2.0"
            }
        """.trimIndent()

        val notification = AppleNotificationParser.parseNotificationPayload(notificationJson)

        notification.notificationType shouldBe "DID_RENEW"
        notification.notificationUUID shouldBe "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee"
        notification.environment shouldBe "Sandbox"
        notification.bundleId shouldBe "com.fancia.app"
        notification.transaction shouldNotBe null
        val tx = notification.transaction!!
        tx.originalTransactionId shouldBe "1000000123456789"
        tx.productId shouldBe "com.fancia.premium.monthly"
        tx.appAccountToken shouldBe "11111111-1111-1111-1111-111111111111"
        tx.expiresAt() shouldNotBe null
    }
})

private fun fakeJws(payloadJson: String): String {
    val header = Base64.getUrlEncoder().withoutPadding()
        .encodeToString("""{"alg":"ES256","typ":"JWT"}""".toByteArray())
    val payload = Base64.getUrlEncoder().withoutPadding()
        .encodeToString(payloadJson.toByteArray())
    return "$header.$payload.fakesignature"
}
