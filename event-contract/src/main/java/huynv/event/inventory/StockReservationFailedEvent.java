package huynv.event.inventory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Defines the payload for the inventory.stock.reservation.failed event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockReservationFailedEvent(UUID orderId, Long tenantId, String reason) {
}

