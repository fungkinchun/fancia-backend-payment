package com.fancia.backend.payment.mapper

import com.fancia.backend.payment.core.entity.Subscription
import com.fancia.backend.shared.user.core.dto.SubscriptionResponse

fun Subscription.toDto(): SubscriptionResponse =
    SubscriptionResponse(
        id = id,
        userId = userId,
        provider = provider,
        providerSubscriptionId = providerSubscriptionId,
        productId = productId,
        status = status,
        expiresAt = expiresAt,
        environment = environment,
        createdAt = createdAt,
        updatedAt = updatedAt,
    )
