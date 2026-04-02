package huynv.event;

/**
 * Defines canonical event type names for payment-related saga events.
 */
public final class PaymentEventTypes {

    public static final String STOCK_RESERVED = "inventory.stock.reserved";
    public static final String PAYMENT_PROCESSING = "payment.processing";
    public static final String PAYMENT_COMPLETED = "payment.completed";
    public static final String PAYMENT_FAILED = "payment.failed";

    private PaymentEventTypes() {
    }
}

