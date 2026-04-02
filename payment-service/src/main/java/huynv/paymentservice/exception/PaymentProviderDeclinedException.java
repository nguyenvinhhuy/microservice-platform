package huynv.paymentservice.exception;

/**
 * Signals that the payment provider declined the charge and retries should not be attempted.
 */
public class PaymentProviderDeclinedException extends PaymentProviderException {

    /**
     * Creates an exception representing a provider decline.
     *
     * @param message Human-readable message for logs and error propagation.
     * @return Initializes a payment provider declined exception.
     */
    public PaymentProviderDeclinedException(String message) {
        super(message);
    }

    /**
     * Creates an exception representing a provider decline with a root cause.
     *
     * @param message Human-readable message for logs and error propagation.
     * @param cause Root cause that triggered the decline.
     * @return Initializes a payment provider declined exception.
     */
    public PaymentProviderDeclinedException(String message, Throwable cause) {
        super(message, cause);
    }
}

