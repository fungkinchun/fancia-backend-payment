package com.fancia.backend.payment.core.service

import com.fancia.backend.payment.core.support.stripe.StripeClient
import com.fancia.backend.shared.payment.core.dto.ConfirmConnectCheckoutPaidRequest
import com.fancia.backend.payment.external.EventReservationInternalClient
import com.fancia.backend.payment.external.VenueBookingInternalClient
import com.fancia.backend.shared.payment.core.enums.ConnectCheckoutPurpose
import com.fancia.backend.shared.payment.core.message.ConnectCheckoutCompletedEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.UUID

@Service
class ConnectCheckoutFulfillmentService(
    private val venueBookingInternalClient: VenueBookingInternalClient,
    private val eventReservationInternalClient: EventReservationInternalClient,
    private val stripeClient: StripeClient,
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun fulfill(event: ConnectCheckoutCompletedEvent) {
        try {
            when (event.purpose) {
                ConnectCheckoutPurpose.VENUE_BOOKING.name -> fulfillVenueBooking(event)
                ConnectCheckoutPurpose.EVENT_TICKET.name -> fulfillEventTicket(event)
                else -> log.info("Skipping Feign fulfillment for unsupported purpose={}", event.purpose)
            }
        } catch (ex: Exception) {
            log.error(
                "Connect checkout fulfill failed purpose={} resourceId={} session={} — refunding",
                event.purpose,
                event.resourceId,
                event.checkoutSessionId,
                ex,
            )
            runCatching {
                stripeClient.refundConnectCheckoutSession(event.checkoutSessionId)
            }.onFailure { refundEx ->
                log.error("Refund failed for session={}", event.checkoutSessionId, refundEx)
            }
            throw ex
        }
    }

    private fun fulfillVenueBooking(event: ConnectCheckoutCompletedEvent) {
        val bookingId = runCatching { UUID.fromString(event.resourceId) }.getOrNull()
            ?: run {
                log.warn("Ignoring venue checkout with invalid resourceId={}", event.resourceId)
                return
            }
        venueBookingInternalClient.confirmPaid(
            bookingId,
            ConfirmConnectCheckoutPaidRequest(event.checkoutSessionId),
        )
    }

    private fun fulfillEventTicket(event: ConnectCheckoutCompletedEvent) {
        val ids = parseEventTicketResourceId(event.resourceId)
            ?: run {
                log.warn("Ignoring event checkout with invalid resourceId={}", event.resourceId)
                return
            }
        val (eventId, occurrenceId, userId) = ids
        eventReservationInternalClient.confirmPaid(
            eventId,
            occurrenceId,
            userId,
            ConfirmConnectCheckoutPaidRequest(event.checkoutSessionId),
        )
    }

    companion object {
        fun parseEventTicketResourceId(resourceId: String): Triple<UUID, UUID, UUID>? {
            val parts = resourceId.split(':')
            if (parts.size != 3) return null
            return runCatching {
                Triple(UUID.fromString(parts[0]), UUID.fromString(parts[1]), UUID.fromString(parts[2]))
            }.getOrNull()
        }
    }
}
