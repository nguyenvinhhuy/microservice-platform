package huynv.paymentservice.dto;

import java.time.OffsetDateTime;

/**
 * Defines a standardized API error response payload.
 */
public record ErrorResponse(
        String code,
        String message,
        OffsetDateTime timestamp
) {
}

