package huynv.orderservice.dto;

import java.math.BigDecimal;

/**
 * Defines a minimal product snapshot returned by product-service for order validation.
 */
public record ProductSnapshotResponse(
        Long id,
        BigDecimal price,
        String currency,
        String status
) {
}

