package huynv.paymentservice.saga;

import huynv.event.BaseEvent;
import huynv.event.inventory.StockReservedEvent;
import huynv.paymentservice.service.PaymentTransactionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.Objects;

/**
 * Handles saga events related to payment processing and enforces consumer idempotency.
 */
@Component
public class PaymentSagaHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentSagaHandler.class);

    private final PaymentTransactionService paymentTransactionService;

    /**
     * Creates a saga handler for payment processing events.
     *
     * @param paymentTransactionService Transactional service used to process payments with atomicity guarantees.
     * @return Initializes a payment saga handler.
     */
    public PaymentSagaHandler(PaymentTransactionService paymentTransactionService) {
        this.paymentTransactionService = paymentTransactionService;
    }

    /**
     * Handles a StockReservedEvent using transactional processing and consumer idempotency.
     *
     * @param event Unified stock reserved event envelope.
     * @return Processes payment and records consumer idempotency marker on success within the same transaction.
     */
    public void handleStockReserved(BaseEvent<StockReservedEvent> event) {
        Objects.requireNonNull(event, "event");
        paymentTransactionService.processFromStockReserved(event);
    }
}

