package huynv.productservice.exception;

public class QuotaExceededException extends RuntimeException {
    /**
     * QuotaExceededException operation.
     *
     * @param message input parameter
     * @return performs side effects defined by this operation
     */
    public QuotaExceededException(String message) {
        super(message);
    }
}
