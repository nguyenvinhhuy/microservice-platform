package huynv.inventoryservice.exception;

public class ReservationNotFoundException extends RuntimeException {
    /**
     * ReservationNotFoundException operation.
     *
     * @param message input parameter
     * @return performs side effects defined by this operation
     */
    public ReservationNotFoundException(String message) {
        super(message);
    }
}
