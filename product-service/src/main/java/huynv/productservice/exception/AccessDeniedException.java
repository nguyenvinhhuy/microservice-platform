package huynv.productservice.exception;

public class AccessDeniedException extends RuntimeException {
    /**
     * AccessDeniedException operation.
     *
     * @param message input parameter
     * @return performs side effects defined by this operation
     */
    public AccessDeniedException(String message) {
        super(message);
    }
}
