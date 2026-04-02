package huynv.paymentservice.domain;

/**
 * Defines the payment lifecycle states enforced by the payment aggregate state machine.
 */
public enum PaymentStatus {
    PENDING,
    PROCESSING,
    SUCCEEDED,
    FAILED,
    CANCELLED
}
