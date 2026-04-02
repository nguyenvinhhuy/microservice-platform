package huynv.orderservice.exception;

public class InvalidOrderStateException extends RuntimeException {
    /**
     * InvalidOrderStateException operation.
     *
     * @param message input parameter
     * @return performs side effects defined by this operation
     */
    public InvalidOrderStateException(String message) {
        super(message);
    }
}
