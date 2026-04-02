package huynv.paymentservice.domain;

/**
 * Defines lifecycle statuses for payment outbox records used for two-phase publishing.
 */
public enum PaymentOutboxStatus {
    NEW,
    PROCESSING,
    PUBLISHED
}

