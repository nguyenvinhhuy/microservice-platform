package huynv.paymentservice.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Defines a payment provider client used to charge an order amount with an idempotency key.
 */
public interface PaymentProviderClient {

    /**
     * Charges the provided amount for the given order and returns a provider transaction identifier.
     *
     * @param orderId Order identifier associated with the charge request.
     * @param amount Amount to charge.
     * @param currency ISO currency code for the amount.
     * @param idempotencyKey Idempotency key used to prevent duplicate charging at the provider.
     * @return Returns a provider transaction identifier when the charge succeeds.
     */
    String charge(UUID orderId, BigDecimal amount, String currency, String idempotencyKey);

    /**
     * Refunds a previously successful transaction and returns a provider refund identifier.
     *
     * @param transactionId Provider transaction identifier to refund.
     * @param amount Amount to refund.
     * @param currency ISO currency code for the amount.
     * @param idempotencyKey Idempotency key used to prevent duplicate refunds at the provider.
     * @return Returns a provider refund identifier when the refund succeeds.
     */
    String refund(String transactionId, BigDecimal amount, String currency, String idempotencyKey);

    /**
     * Returns the provider status for a previously created transaction.
     *
     * @param transactionId Provider transaction identifier.
     * @return Returns the provider status for the transaction.
     */
    PaymentProviderTransactionStatus getTransactionStatus(String transactionId);
}
