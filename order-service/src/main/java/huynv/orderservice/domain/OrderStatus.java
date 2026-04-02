package huynv.orderservice.domain;

public enum OrderStatus {
    CREATED,
    RESERVED,
    PAYMENT_IN_PROGRESS,
    CONFIRMED,
    CANCELLED,
    FAILED,
    COMPENSATING
}
