package huynv.eventinfra.outbox;

/**
 * Enumerates Kafka outbox publishing state transitions for reliable at-least-once delivery.
 */
public enum KafkaOutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED,
    DLQED
}

