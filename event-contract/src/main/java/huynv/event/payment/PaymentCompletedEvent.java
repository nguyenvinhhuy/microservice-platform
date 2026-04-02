package huynv.event.payment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.UUID;

/**
 * Defines the payload for the payment.completed event.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentCompletedEvent(
        UUID orderId,
        UUID paymentId,
        Long tenantId,
        String transactionId
) {
}

