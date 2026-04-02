package huynv.paymentservice.domain;

import huynv.paymentservice.exception.IllegalPaymentStateTransitionException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Tests payment aggregate state machine transitions and invariants.
 */
public class PaymentStateMachineTest {

    /**
     * Verifies a payment transitions from PENDING to PROCESSING to SUCCEEDED and stores transaction id.
     *
     * @return Asserts the payment status and transaction id are updated correctly.
     */
    @Test
    public void shouldTransitionToSucceeded() {
        OffsetDateTime now = OffsetDateTime.now();
        Payment payment = Payment.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                BigDecimal.valueOf(100),
                "USD",
                "simulated",
                "idem-1",
                "corr-1",
                "trace-1",
                now
        );

        payment.markProcessing(now);
        payment.markSucceeded("tx-1", now);

        assertEquals(PaymentStatus.SUCCEEDED, payment.getStatus());
        assertEquals("tx-1", payment.getTransactionId());
    }

    /**
     * Verifies illegal transitions are rejected by the aggregate.
     *
     * @return Asserts an IllegalPaymentStateTransitionException is thrown for invalid transitions.
     */
    @Test
    public void shouldRejectIllegalTransition() {
        OffsetDateTime now = OffsetDateTime.now();
        Payment payment = Payment.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                BigDecimal.valueOf(100),
                "USD",
                "simulated",
                "idem-2",
                "corr-2",
                "trace-2",
                now
        );

        assertThrows(IllegalPaymentStateTransitionException.class, () -> payment.markSucceeded("tx-x", now));
        assertEquals(PaymentStatus.PENDING, payment.getStatus());
    }

    /**
     * Verifies cancelling a succeeded payment is rejected by the aggregate.
     *
     * @return Asserts an IllegalPaymentStateTransitionException is thrown for cancellation after success.
     */
    @Test
    public void shouldRejectCancelAfterSuccess() {
        OffsetDateTime now = OffsetDateTime.now();
        Payment payment = Payment.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                1L,
                BigDecimal.valueOf(100),
                "USD",
                "simulated",
                "idem-3",
                "corr-3",
                "trace-3",
                now
        );

        payment.markProcessing(now);
        payment.markSucceeded("tx-2", now);

        assertNotNull(payment.getTransactionId());
        assertThrows(IllegalPaymentStateTransitionException.class, () -> payment.cancel(now));
    }
}
