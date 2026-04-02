package huynv.notificationservice.exception;

/**
 * Signals that notification delivery failed and should be retried or dead-lettered.
 */
public class NotificationDeliveryException extends RuntimeException {

    /**
     * Creates a delivery exception for a failed notification attempt.
     *
     * @param message Message describing the delivery failure.
     * @return Initializes a delivery exception instance.
     */
    public NotificationDeliveryException(String message) {
        super(message);
    }

    /**
     * Creates a delivery exception with an underlying cause.
     *
     * @param message Message describing the delivery failure.
     * @param cause Underlying cause of the delivery failure.
     * @return Initializes a delivery exception instance.
     */
    public NotificationDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}

