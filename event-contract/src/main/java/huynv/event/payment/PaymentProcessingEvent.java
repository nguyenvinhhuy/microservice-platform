package huynv.event.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Defines the payload for the payment.processing event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentProcessingEvent(
        UUID orderId,
        UUID paymentId,
        Long tenantId
) {
}

