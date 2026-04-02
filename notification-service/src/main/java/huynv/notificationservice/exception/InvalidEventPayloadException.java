package huynv.notificationservice.exception;

/**
 * Signals that an inbound event payload is invalid and cannot be processed safely.
 */
public class InvalidEventPayloadException extends RuntimeException {

    /**
     * Creates an exception for an invalid event payload.
     *
     * @param message Message describing the validation failure.
     * @return Initializes an invalid event payload exception instance.
     */
    public InvalidEventPayloadException(String message) {
        super(message);
    }

    /**
     * Creates an exception for an invalid event payload with an underlying cause.
     *
     * @param message Message describing the validation failure.
     * @param cause Underlying cause of the failure.
     * @return Initializes an invalid event payload exception instance.
     */
    public InvalidEventPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}

