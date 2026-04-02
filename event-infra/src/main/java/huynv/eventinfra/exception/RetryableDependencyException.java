package huynv.eventinfra.exception;

/**
 * Indicates a downstream dependency failed transiently and the current operation should be retried.
 */
public class RetryableDependencyException extends RuntimeException {

    /**
     * Creates a retryable dependency exception with a message.
     *
     * @param message Error message describing the transient dependency failure.
     * @return Initializes a retryable dependency exception.
     */
    public RetryableDependencyException(String message) {
        super(message);
    }

    /**
     * Creates a retryable dependency exception with a message and root cause.
     *
     * @param message Error message describing the transient dependency failure.
     * @param cause Root cause of the failure.
     * @return Initializes a retryable dependency exception.
     */
    public RetryableDependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}


