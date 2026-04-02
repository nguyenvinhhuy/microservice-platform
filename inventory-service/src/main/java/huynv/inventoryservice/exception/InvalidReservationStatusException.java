package huynv.inventoryservice.exception;

public class InvalidReservationStatusException extends RuntimeException {
    /**
     * InvalidReservationStatusException operation.
     *
     * @param message input parameter
     * @return performs side effects defined by this operation
     */
    public InvalidReservationStatusException(String message) {
        super(message);
    }
}
