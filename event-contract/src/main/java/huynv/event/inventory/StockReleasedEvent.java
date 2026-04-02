package huynv.event.inventory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.UUID;

/**
 * Defines the payload for the inventory.stock.released event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockReleasedEvent(UUID orderId, Long tenantId, List<StockItem> items) {
}

