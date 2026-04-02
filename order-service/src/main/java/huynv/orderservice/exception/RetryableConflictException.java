package huynv.orderservice.exception;

public class RetryableConflictException extends RuntimeException {
    /**
     * RetryableConflictException operation.
     *
     * @param message input parameter
     * @param cause input parameter
     * @return performs side effects defined by this operation
     */
    public RetryableConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
