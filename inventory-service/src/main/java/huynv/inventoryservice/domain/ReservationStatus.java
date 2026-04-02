package huynv.inventoryservice.domain;

// Enum representing the possible statuses of an inventory reservation.
public enum ReservationStatus {
    RESERVED,   // Stock is reserved, awaiting confirmation or release.
    CONFIRMED,  // Stock reservation has been confirmed, stock is allocated.
    RELEASED    // Stock reservation has been released, stock is returned to available.
}
