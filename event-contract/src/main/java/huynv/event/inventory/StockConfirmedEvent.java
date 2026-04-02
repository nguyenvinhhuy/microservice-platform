package huynv.event.inventory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/**
 * Defines the payload for the inventory.stock.confirmed event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockConfirmedEvent(UUID orderId, Long tenantId, List<StockItem> items) {
}

