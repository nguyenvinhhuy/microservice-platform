package huynv.paymentservice.exception;

/**
 * Represents an unrecoverable domain rule violation within the payment aggregate.
 */
public class PaymentDomainException extends RuntimeException {

    /**
     * Creates a domain exception with a human-readable message.
     *
     * @param message Error message describing the domain rule violation.
     * @return Constructs a domain exception instance.
     */
    public PaymentDomainException(String message) {
        super(message);
    }

    /**
     * Creates a domain exception with a human-readable message and root cause.
     *
     * @param message Error message describing the domain rule violation.
     * @param cause Root cause that triggered the domain rule violation.
     * @return Constructs a domain exception instance with a root cause.
     */
    public PaymentDomainException(String message, Throwable cause) {
        super(message, cause);
    }
}
