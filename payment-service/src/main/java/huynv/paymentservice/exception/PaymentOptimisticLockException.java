package huynv.paymentservice.exception;

/**
 * Signals an optimistic locking conflict while mutating payment state.
 */
public class PaymentOptimisticLockException extends PaymentDomainException {

    /**
     * Creates an exception indicating an optimistic locking conflict occurred.
     *
     * @param message Human-readable message for logs and API responses.
     * @return Initializes an exception indicating an optimistic locking conflict occurred.
     */
    public PaymentOptimisticLockException(String message) {
        super(message);
    }
}

