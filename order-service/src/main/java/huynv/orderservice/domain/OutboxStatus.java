package huynv.orderservice.domain;

public enum OutboxStatus {
    PENDING,
    PROCESSING,
    SENT,
    FAILED
}
