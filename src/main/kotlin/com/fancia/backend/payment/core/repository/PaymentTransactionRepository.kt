package com.fancia.backend.payment.core.repository

import com.fancia.backend.payment.core.entity.PaymentTransaction
import com.fancia.backend.shared.user.core.enums.PaymentProvider
import org.springframework.data.jpa.repository.JpaRepository
import java.util.Optional
import java.util.UUID

interface PaymentTransactionRepository : JpaRepository<PaymentTransaction, UUID> {
    fun findByProviderAndProviderTransactionId(
        provider: PaymentProvider,
        providerTransactionId: String,
    ): Optional<PaymentTransaction>

    fun findByUserIdOrderByPaidAtDescCreatedAtDesc(userId: UUID): List<PaymentTransaction>
}
