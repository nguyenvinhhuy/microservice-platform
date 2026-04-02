package huynv.orderservice.exception;

public class PaymentFailedException extends RuntimeException {
    /**
     * PaymentFailedException operation.
     *
     * @param message input parameter
     * @return performs side effects defined by this operation
     */
    public PaymentFailedException(String message) {
        super(message);
    }

    /**
     * PaymentFailedException operation.
     *
     * @param message input parameter
     * @param cause input parameter
     * @return performs side effects defined by this operation
     */
    public PaymentFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
