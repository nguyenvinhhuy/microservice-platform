package huynv.notificationservice.domain;

/**
 * Enumerates stable notification types derived from platform event types.
 */
public enum NotificationType {
    ORDER_CREATED,
    ORDER_CANCELLED,
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED
}

