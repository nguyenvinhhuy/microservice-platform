package huynv.event.product;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;

/**
 * Defines the payload for the product.updated event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductUpdatedEvent(
        Long tenantId,
        Long productId,
        String code,
        String name,
        BigDecimal price,
        String currency
) {
}

