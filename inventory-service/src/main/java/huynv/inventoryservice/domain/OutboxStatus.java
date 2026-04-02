package huynv.inventoryservice.domain;

/**
 * Represents the lifecycle status of an outbox row used for reliable event publishing.
 */
public enum OutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}

