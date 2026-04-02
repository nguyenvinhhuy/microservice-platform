package huynv.event.inventory;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Represents one stock item entry within an inventory integration event payload.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record StockItem(Long productId, Integer quantity) {
}

