package huynv.productservice.model;

/**
 * Represents the lifecycle status of a product outbox row used for reliable publishing.
 */
public enum OutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}

