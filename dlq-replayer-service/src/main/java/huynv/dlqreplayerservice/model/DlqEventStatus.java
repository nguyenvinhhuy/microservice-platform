package huynv.dlqreplayerservice.model;

/**
 * Defines the lifecycle status of a DLQ event stored for manual recovery.
 */
public enum DlqEventStatus {
    PENDING,
    REPLAYED,
    SKIPPED
}

