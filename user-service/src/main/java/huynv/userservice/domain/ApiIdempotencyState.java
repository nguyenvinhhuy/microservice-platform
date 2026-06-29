package huynv.userservice.domain;

/**
 * Defines the lifecycle states for persisted REST API idempotency records.
 */
public enum ApiIdempotencyState {
    PROCESSING,
    COMPLETED,
    FAILED
}

