package com.fancia.backend.payment.core.support.google

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.util.Base64

class GoogleNotificationParserTest : FunSpec({
    test("parses Pub/Sub push and subscription notification") {
        val developerJson = """
            {
              "version":"1.0",
              "packageName":"com.fancia.app",
              "eventTimeMillis":"1000000000000",
              "subscriptionNotification":{
                "version":"1.0",
                "notificationType":4,
                "purchaseToken":"opaque-purchase-token",
                "subscriptionId":"premium_monthly"
              }
            }
        """.trimIndent()
        val data = Base64.getEncoder().encodeToString(developerJson.toByteArray())
        val pushBody = """
            {
              "message": {
                "data": "$data",
                "messageId": "msg-123",
                "publishTime": "2026-01-01T00:00:00.000Z"
              },
              "subscription": "projects/demo/subscriptions/play-push"
            }
        """.trimIndent()

        val push = GoogleNotificationParser.parsePubSubPush(pushBody)
        push.message?.resolvedMessageId() shouldBe "msg-123"

        val notification = GoogleNotificationParser.decodeDeveloperNotification(data)
        notification.packageName shouldBe "com.fancia.app"
        notification.isTest() shouldBe false
        notification.subscriptionNotification shouldNotBe null
        val sub = notification.subscriptionNotification!!
        sub.notificationType shouldBe 4
        sub.purchaseToken shouldBe "opaque-purchase-token"
        sub.subscriptionId shouldBe "premium_monthly"
        GoogleSubscriptionNotificationType.nameOf(4) shouldBe "SUBSCRIPTION_PURCHASED"
    }

    test("detects test notification") {
        val developerJson = """{"version":"1.0","packageName":"com.fancia.app","eventTimeMillis":"1","testNotification":{"version":"1.0"}}"""
        val data = Base64.getEncoder().encodeToString(developerJson.toByteArray())
        val notification = GoogleNotificationParser.decodeDeveloperNotification(data)
        notification.isTest() shouldBe true
        notification.subscriptionNotification shouldBe null
    }
})
