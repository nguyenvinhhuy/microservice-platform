package huynv.paymentservice.exception;

/**
 * Signals that a provider call timed out and may succeed on a later retry.
 */
public class PaymentProviderTimeoutException extends PaymentProviderException {

    /**
     * Creates an exception representing a provider timeout.
     *
     * @param message Human-readable message for logs and error propagation.
     * @return Initializes a payment provider timeout exception.
     */
    public PaymentProviderTimeoutException(String message) {
        super(message);
    }

    /**
     * Creates an exception representing a provider timeout with a root cause.
     *
     * @param message Human-readable message for logs and error propagation.
     * @param cause Root cause that triggered the timeout.
     * @return Initializes a payment provider timeout exception.
     */
    public PaymentProviderTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}

