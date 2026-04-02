package huynv.orderservice.saga;

/**
 * Defines persisted saga step contract for order orchestration.
 * Each step is executed with AT_LEAST_ONCE processing semantics.
 * Step handlers must therefore be idempotent and crash-safe by reading persisted saga/order state.
 */
public enum OrderSagaState {
    /**
     * Delivery semantics: AT_LEAST_ONCE.
     * Reversibility: reversible by releasing reservation.
     * Compensation: transition to COMPENSATING and release stock.
     * Crash recovery: safe to replay reservation because inventory contract is idempotent.
     */
    RESERVE_STOCK,
    /**
     * Delivery semantics: AT_LEAST_ONCE.
     * Reversibility: reversible via payment refund when available.
     * Compensation: set COMPENSATING then refund and release stock.
     * Crash recovery: safe by checking persisted paymentId before re-charging.
     */
    CHARGE_PAYMENT,
    /**
     * Delivery semantics: AT_LEAST_ONCE.
     * Reversibility: reversible if confirm fails after charge by refund compensation.
     * Compensation: move to COMPENSATING and run refund + release.
     * Crash recovery: safe to replay confirm because inventory confirm is idempotent.
     */
    CONFIRM_STOCK,
    /**
     * Delivery semantics: AT_MOST_ONCE terminal state transition.
     * Reversibility: not reversible by saga once completed.
     * Compensation: no further compensation required.
     * Crash recovery: no-op on resume worker.
     */
    COMPLETED,
    /**
     * Delivery semantics: AT_LEAST_ONCE.
     * Reversibility: exits to COMPLETED when compensation succeeds.
     * Compensation: retries refund/release until successful or operator intervention.
     * Crash recovery: safe to replay using persisted paymentId and retry counter.
     */
    COMPENSATING
}
