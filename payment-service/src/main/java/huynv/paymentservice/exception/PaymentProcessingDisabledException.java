package huynv.paymentservice.exception;

/**
 * Signals that payment processing has been disabled by configuration as a safety kill switch.
 */
public class PaymentProcessingDisabledException extends PaymentDomainException {

    /**
     * Creates an exception indicating payment processing is disabled.
     *
     * @param message Human-readable message for logs and API responses.
     * @return Initializes an exception indicating payment processing is disabled.
     */
    public PaymentProcessingDisabledException(String message) {
        super(message);
    }
}

