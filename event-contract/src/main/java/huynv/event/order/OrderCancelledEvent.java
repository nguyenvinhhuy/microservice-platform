package huynv.event.order;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.Instant;
import java.util.UUID;

/**
 * Defines the payload for the order.cancelled event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record OrderCancelledEvent(
        UUID eventId,
        int eventVersion,
        String correlationId,
        String causationId,
        UUID orderId,
        Long tenantId,
        Long userId,
        String status,
        Instant timestamp
) {
}

