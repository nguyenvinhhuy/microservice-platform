package huynv.paymentservice.exception;

/**
 * Signals that an inbound message is invalid and should be routed to the dead-letter topic without retries.
 */
public class NonRetryableMessageException extends PaymentDomainException {

    /**
     * Creates an exception indicating the message is non-retryable.
     *
     * @param message Human-readable reason for dead-letter routing.
     * @return Initializes a non-retryable message exception.
     */
    public NonRetryableMessageException(String message) {
        super(message);
    }

    /**
     * Creates an exception indicating the message is non-retryable with a root cause.
     *
     * @param message Human-readable reason for dead-letter routing.
     * @param cause Root cause that triggered parsing or validation failure.
     * @return Initializes a non-retryable message exception.
     */
    public NonRetryableMessageException(String message, Throwable cause) {
        super(message, cause);
    }
}

