package huynv.fileservice.domain;

/**
 * Defines the processing states for persisted API idempotency records.
 */
public enum ApiIdempotencyStatus {
    PROCESSING,
    COMPLETED,
    FAILED
}

