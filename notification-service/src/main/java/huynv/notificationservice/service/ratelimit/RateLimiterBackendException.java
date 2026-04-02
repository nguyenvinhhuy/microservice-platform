package huynv.notificationservice.service.ratelimit;

/**
 * Signals that the primary rate limiting backend failed and the caller should use a safe fallback.
 */
public class RateLimiterBackendException extends RuntimeException {

    /**
     * Creates a backend exception with a message and root cause.
     *
     * @param message Error message describing the backend failure.
     * @param cause Root cause of the failure.
     * @return Initializes a rate limiter backend exception.
     */
    public RateLimiterBackendException(String message, Throwable cause) {
        super(message, cause);
    }
}

