package huynv.event.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Defines the payload for the payment.failed event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentFailedEvent(
        UUID orderId,
        UUID paymentId,
        Long tenantId,
        String reason
) {
}

