package huynv.paymentservice.exception;

/**
 * Represents a payment-service exception that should be handled as a client-visible error.
 */
public class PaymentException extends PaymentDomainException {

    /**
     * Creates a payment exception with a human-readable message.
     *
     * @param message Error message describing the failure.
     * @return Constructs a payment exception instance.
     */
    public PaymentException(String message) {
        super(message);
    }

    /**
     * Creates a payment exception with a human-readable message and root cause.
     *
     * @param message Error message describing the failure.
     * @param cause Root cause that triggered the failure.
     * @return Constructs a payment exception instance with a root cause.
     */
    public PaymentException(String message, Throwable cause) {
        super(message, cause);
    }
}

