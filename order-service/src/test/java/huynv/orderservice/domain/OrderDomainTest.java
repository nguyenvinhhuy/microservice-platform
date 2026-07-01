package huynv.orderservice.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for Order domain state machine — no Spring context, no mocks.
 */
class OrderDomainTest {

    /**
     * Builds a minimal Order with the given status for use in assertion-focused tests.
     *
     * @param status The order status to assign to the newly created Order.
     * @return A fully built Order instance with fixed tenant, user, amount, and empty items.
     */
    private Order orderWith(OrderStatus status) {
        return Order.builder()
                .id(UUID.randomUUID())
                .tenantId(1L)
                .userId(100L)
                .status(status)
                .totalAmount(BigDecimal.TEN)
                .currency("USD")
                .orderItems(List.of())
                .paymentAttemptCount(0)
                .build();
    }

    /**
     * Verifies that a CREATED order transitions to RESERVED when reservation succeeds,
     * and that the reservation reference is stored and failure reason remains null.
     *
     * @return Asserts status equals RESERVED and reservationReference equals the supplied ref.
     */
    @Test
    void markReservationSucceeded_transitions_CREATED_to_RESERVED() {
        Order order = orderWith(OrderStatus.CREATED);
        order.markReservationSucceeded("ref-1");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.RESERVED);
        assertThat(order.getReservationReference()).isEqualTo("ref-1");
        assertThat(order.getFailureReason()).isNull();
    }

    /**
     * Verifies that calling markReservationSucceeded on an order not in CREATED status
     * throws a DomainInvariantViolationException to enforce the state machine contract.
     *
     * @return Asserts that DomainInvariantViolationException is thrown.
     */
    @Test
    void markReservationSucceeded_throws_when_not_CREATED() {
        Order order = orderWith(OrderStatus.RESERVED);
        assertThatThrownBy(() -> order.markReservationSucceeded("ref"))
                .isInstanceOf(DomainInvariantViolationException.class);
    }

    /**
     * Verifies that a CREATED order transitions to FAILED when the reservation fails,
     * and that the supplied failure reason is persisted on the order.
     *
     * @return Asserts status equals FAILED and failureReason equals the supplied message.
     */
    @Test
    void markReservationFailed_transitions_CREATED_to_FAILED() {
        Order order = orderWith(OrderStatus.CREATED);
        order.markReservationFailed("out of stock");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(order.getFailureReason()).isEqualTo("out of stock");
    }

    /**
     * Verifies that calling markReservationFailed on an order not in CREATED status
     * throws a DomainInvariantViolationException to enforce the state machine contract.
     *
     * @return Asserts that DomainInvariantViolationException is thrown.
     */
    @Test
    void markReservationFailed_throws_when_not_CREATED() {
        Order order = orderWith(OrderStatus.RESERVED);
        assertThatThrownBy(() -> order.markReservationFailed("reason"))
                .isInstanceOf(DomainInvariantViolationException.class);
    }

    /**
     * Verifies that a RESERVED order transitions to PAYMENT_IN_PROGRESS when a payment attempt begins,
     * and that the payment attempt counter is incremented to 1.
     *
     * @return Asserts status equals PAYMENT_IN_PROGRESS and paymentAttemptCount equals 1.
     */
    @Test
    void beginPaymentAttempt_transitions_RESERVED_to_PAYMENT_IN_PROGRESS_and_increments_counter() {
        Order order = orderWith(OrderStatus.RESERVED);
        order.beginPaymentAttempt();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAYMENT_IN_PROGRESS);
        assertThat(order.getPaymentAttemptCount()).isEqualTo(1);
    }

    /**
     * Verifies that the payment attempt counter increments to exactly 1 after the first attempt,
     * confirming monotonic counting behaviour for audit and retry-limit enforcement.
     *
     * @return Asserts paymentAttemptCount equals 1 after a single beginPaymentAttempt call.
     */
    @Test
    void beginPaymentAttempt_increments_counter_monotonically_on_each_attempt() {
        Order order = orderWith(OrderStatus.RESERVED);
        order.beginPaymentAttempt();
        assertThat(order.getPaymentAttemptCount()).isEqualTo(1);
    }

    /**
     * Verifies that calling beginPaymentAttempt on an order not in RESERVED status
     * throws a DomainInvariantViolationException to enforce the state machine contract.
     *
     * @return Asserts that DomainInvariantViolationException is thrown.
     */
    @Test
    void beginPaymentAttempt_throws_when_not_RESERVED() {
        Order order = orderWith(OrderStatus.CREATED);
        assertThatThrownBy(order::beginPaymentAttempt)
                .isInstanceOf(DomainInvariantViolationException.class);
    }

    /**
     * Verifies that a PAYMENT_IN_PROGRESS order transitions to CONFIRMED when payment succeeds,
     * and that no failure reason is recorded on the confirmed order.
     *
     * @return Asserts status equals CONFIRMED and failureReason is null.
     */
    @Test
    void markPaid_transitions_PAYMENT_IN_PROGRESS_to_CONFIRMED() {
        Order order = orderWith(OrderStatus.PAYMENT_IN_PROGRESS);
        order.markPaid();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.getFailureReason()).isNull();
    }

    /**
     * Verifies that calling markPaid on an order not in PAYMENT_IN_PROGRESS status
     * throws a DomainInvariantViolationException to enforce the state machine contract.
     *
     * @return Asserts that DomainInvariantViolationException is thrown.
     */
    @Test
    void markPaid_throws_when_not_PAYMENT_IN_PROGRESS() {
        Order order = orderWith(OrderStatus.RESERVED);
        assertThatThrownBy(order::markPaid)
                .isInstanceOf(DomainInvariantViolationException.class);
    }

    /**
     * Verifies that a PAYMENT_IN_PROGRESS order transitions to FAILED when payment fails,
     * and that the supplied failure reason is persisted on the order.
     *
     * @return Asserts status equals FAILED and failureReason equals the supplied message.
     */
    @Test
    void markPaymentFailed_transitions_PAYMENT_IN_PROGRESS_to_FAILED() {
        Order order = orderWith(OrderStatus.PAYMENT_IN_PROGRESS);
        order.markPaymentFailed("insufficient funds");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.FAILED);
        assertThat(order.getFailureReason()).isEqualTo("insufficient funds");
    }

    /**
     * Verifies that calling markCancelled on an already CANCELLED order is idempotent,
     * leaving the status unchanged rather than throwing an exception.
     *
     * @return Asserts status remains CANCELLED after the repeated call.
     */
    @Test
    void markCancelled_is_idempotent_when_already_CANCELLED() {
        Order order = orderWith(OrderStatus.CANCELLED);
        order.markCancelled();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    /**
     * Verifies that cancelling a CONFIRMED order throws a DomainInvariantViolationException
     * with a message that identifies the illegal CONFIRMED state.
     *
     * @return Asserts that DomainInvariantViolationException is thrown containing "CONFIRMED".
     */
    @Test
    void markCancelled_throws_when_CONFIRMED() {
        Order order = orderWith(OrderStatus.CONFIRMED);
        assertThatThrownBy(order::markCancelled)
                .isInstanceOf(DomainInvariantViolationException.class)
                .hasMessageContaining("CONFIRMED");
    }

    /**
     * Verifies that a CREATED order can be cancelled before any stock reservation takes place,
     * transitioning its status directly to CANCELLED.
     *
     * @return Asserts status equals CANCELLED after calling markCancelled on a CREATED order.
     */
    @Test
    void markCancelled_transitions_from_CREATED() {
        Order order = orderWith(OrderStatus.CREATED);
        order.markCancelled();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    /**
     * Verifies that a RESERVED order can be cancelled after stock has been reserved,
     * transitioning its status to CANCELLED so compensation can release the stock.
     *
     * @return Asserts status equals CANCELLED after calling markCancelled on a RESERVED order.
     */
    @Test
    void markCancelled_transitions_from_RESERVED() {
        Order order = orderWith(OrderStatus.RESERVED);
        order.markCancelled();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    /**
     * Verifies that a RESERVED order transitions to COMPENSATING when compensation is triggered,
     * and that the supplied failure reason is recorded for diagnostic purposes.
     *
     * @return Asserts status equals COMPENSATING and failureReason equals the supplied message.
     */
    @Test
    void markCompensating_transitions_RESERVED_to_COMPENSATING() {
        Order order = orderWith(OrderStatus.RESERVED);
        order.markCompensating("payment failed");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPENSATING);
        assertThat(order.getFailureReason()).isEqualTo("payment failed");
    }

    /**
     * Verifies that a PAYMENT_IN_PROGRESS order transitions to COMPENSATING when a mid-payment
     * failure triggers the compensation saga step, such as a network error during charge.
     *
     * @return Asserts status equals COMPENSATING after markCompensating is called.
     */
    @Test
    void markCompensating_transitions_PAYMENT_IN_PROGRESS_to_COMPENSATING() {
        Order order = orderWith(OrderStatus.PAYMENT_IN_PROGRESS);
        order.markCompensating("network error");
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPENSATING);
    }

    /**
     * Verifies that calling markCompensating on a CREATED order throws a DomainInvariantViolationException,
     * since compensation is only valid after stock has been reserved.
     *
     * @return Asserts that DomainInvariantViolationException is thrown.
     */
    @Test
    void markCompensating_throws_when_CREATED() {
        Order order = orderWith(OrderStatus.CREATED);
        assertThatThrownBy(() -> order.markCompensating("reason"))
                .isInstanceOf(DomainInvariantViolationException.class);
    }
}
