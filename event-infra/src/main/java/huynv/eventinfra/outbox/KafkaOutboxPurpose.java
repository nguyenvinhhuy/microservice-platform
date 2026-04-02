package huynv.eventinfra.outbox;

/**
 * Defines the reason a Kafka outbox message exists to support separate backlogs and alerts.
 */
public enum KafkaOutboxPurpose {
    INTERNAL,
    DISPATCH,
    RETRY,
    DLQ,
    DLQ_REPLAY
}

