package huynv.event.order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Defines the payload for the order.created event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCreatedEvent(
        UUID eventId,
        int eventVersion,
        String correlationId,
        String causationId,
        UUID orderId,
        Long tenantId,
        Long userId,
        String status,
        BigDecimal totalAmount,
        String currency,
        Instant timestamp
) {
}

