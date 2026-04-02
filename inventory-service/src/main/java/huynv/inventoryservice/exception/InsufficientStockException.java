package huynv.inventoryservice.exception;

public class InsufficientStockException extends RuntimeException {
    /**
     * InsufficientStockException operation.
     *
     * @param message input parameter
     * @return performs side effects defined by this operation
     */
    public InsufficientStockException(String message) {
        super(message);
    }
}
