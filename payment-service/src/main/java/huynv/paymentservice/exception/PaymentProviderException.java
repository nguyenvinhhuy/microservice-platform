package huynv.paymentservice.exception;

/**
 * Signals a payment provider integration failure that prevented charging the customer.
 */
public class PaymentProviderException extends PaymentDomainException {

    /**
     * Creates an exception representing a provider integration failure.
     *
     * @param message Human-readable message for logs and error propagation.
     * @return Initializes a payment provider exception.
     */
    public PaymentProviderException(String message) {
        super(message);
    }

    /**
     * Creates an exception representing a provider integration failure with a root cause.
     *
     * @param message Human-readable message for logs and error propagation.
     * @param cause Root cause that triggered the provider failure.
     * @return Initializes a payment provider exception.
     */
    public PaymentProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}

