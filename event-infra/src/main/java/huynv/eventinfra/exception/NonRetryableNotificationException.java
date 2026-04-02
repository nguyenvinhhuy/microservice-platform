package huynv.eventinfra.exception;

/**
 * Indicates a notification record failed due to a permanent condition and should be routed directly to the DLQ.
 */
public class NonRetryableNotificationException extends RuntimeException {

    /**
     * Creates a non-retryable exception with a message.
     *
     * @param message Error message describing the permanent failure.
     * @return Initializes a non-retryable notification exception.
     */
    public NonRetryableNotificationException(String message) {
        super(message);
    }

    /**
     * Creates a non-retryable exception with a message and root cause.
     *
     * @param message Error message describing the permanent failure.
     * @param cause Root cause of the failure.
     * @return Initializes a non-retryable notification exception.
     */
    public NonRetryableNotificationException(String message, Throwable cause) {
        super(message, cause);
    }
}


