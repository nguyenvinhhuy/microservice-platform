package huynv.orderservice.exception;

public class InventoryReservationFailedException extends RuntimeException {
    /**
     * InventoryReservationFailedException operation.
     *
     * @param message input parameter
     * @return performs side effects defined by this operation
     */
    public InventoryReservationFailedException(String message) {
        super(message);
    }

    /**
     * InventoryReservationFailedException operation.
     *
     * @param message input parameter
     * @param cause input parameter
     * @return performs side effects defined by this operation
     */
    public InventoryReservationFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
