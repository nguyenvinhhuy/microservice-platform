package huynv.orderservice.exception;

public class OrderNotFoundException extends RuntimeException {
    /**
     * OrderNotFoundException operation.
     *
     * @param message input parameter
     * @return performs side effects defined by this operation
     */
    public OrderNotFoundException(String message) {
        super(message);
    }
}
