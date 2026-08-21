package com.fancia.backend.payment.core.repository

import com.fancia.backend.payment.core.entity.StripeConnectedAccount
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.util.Optional
import java.util.UUID

interface StripeConnectedAccountRepository : JpaRepository<StripeConnectedAccount, UUID> {
    @Query("SELECT a FROM StripeConnectedAccount a WHERE a.user.id = :userId")
    fun findByUserId(@Param("userId") userId: UUID): Optional<StripeConnectedAccount>

    fun findByProviderId(providerId: String): Optional<StripeConnectedAccount>
}
