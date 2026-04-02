package huynv.paymentservice.service;

import huynv.paymentservice.exception.PaymentProviderDeclinedException;
import huynv.paymentservice.exception.PaymentProviderTimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.UUID;

/**
 * Provides a deterministic simulated payment provider for local development and tests.
 */
@Component
public class SimulatedPaymentProviderClient implements PaymentProviderClient {

    private final boolean enabled;

    /**
     * Creates a simulated provider client.
     *
     * @param enabled Flag indicating whether the simulated provider is enabled.
     * @return Initializes a simulated provider client.
     */
    public SimulatedPaymentProviderClient(@Value("${payment.provider.simulated.enabled:true}") boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Charges the order using a deterministic rule set derived from the idempotency key.
     *
     * @param orderId Order identifier associated with the charge request.
     * @param amount Amount to charge.
     * @param currency ISO currency code for the amount.
     * @param idempotencyKey Idempotency key used to prevent duplicate charging at the provider.
     * @return Returns a deterministic provider transaction identifier when the charge succeeds.
     */
    @Override
    public String charge(UUID orderId, BigDecimal amount, String currency, String idempotencyKey) {
        if (!enabled) {
            throw new PaymentProviderDeclinedException("Simulated provider is disabled.");
        }

        String key = idempotencyKey == null ? "" : idempotencyKey.toLowerCase(Locale.ROOT);
        if (key.contains("timeout")) {
            throw new PaymentProviderTimeoutException("Simulated provider timeout for idempotencyKey=" + idempotencyKey + ".");
        }
        if (key.contains("decline") || key.contains("fail")) {
            throw new PaymentProviderDeclinedException("Simulated provider declined the charge for idempotencyKey=" + idempotencyKey + ".");
        }

        String suffix = idempotencyKey == null ? "unknown" : idempotencyKey.replaceAll("[^a-zA-Z0-9]", "");
        suffix = suffix.length() > 12 ? suffix.substring(0, 12) : suffix;
        return "sim-tx-" + orderId.toString().substring(0, 8) + "-" + suffix;
    }

    /**
     * Refunds a transaction using a deterministic rule set derived from the idempotency key.
     *
     * @param transactionId Provider transaction identifier to refund.
     * @param amount Amount to refund.
     * @param currency ISO currency code for the amount.
     * @param idempotencyKey Idempotency key used to prevent duplicate refunds at the provider.
     * @return Returns a deterministic provider refund identifier when the refund succeeds.
     */
    @Override
    public String refund(String transactionId, BigDecimal amount, String currency, String idempotencyKey) {
        if (!enabled) {
            throw new PaymentProviderDeclinedException("Simulated provider is disabled.");
        }
        if (transactionId == null || transactionId.isBlank()) {
            throw new PaymentProviderDeclinedException("Simulated provider refund requires a transactionId.");
        }
        String key = idempotencyKey == null ? "" : idempotencyKey.toLowerCase(Locale.ROOT);
        if (key.contains("timeout")) {
            throw new PaymentProviderTimeoutException("Simulated provider refund timeout for idempotencyKey=" + idempotencyKey + ".");
        }
        if (key.contains("decline") || key.contains("fail")) {
            throw new PaymentProviderDeclinedException("Simulated provider declined the refund for idempotencyKey=" + idempotencyKey + ".");
        }

        String suffix = idempotencyKey == null ? "unknown" : idempotencyKey.replaceAll("[^a-zA-Z0-9]", "");
        suffix = suffix.length() > 12 ? suffix.substring(0, 12) : suffix;
        return "sim-rf-" + transactionId + "-" + suffix;
    }

    /**
     * Returns a deterministic transaction status for reconciliation based on the transaction identifier.
     *
     * @param transactionId Provider transaction identifier.
     * @return Returns the simulated provider status for the transaction.
     */
    @Override
    public PaymentProviderTransactionStatus getTransactionStatus(String transactionId) {
        if (transactionId == null || transactionId.isBlank()) {
            return PaymentProviderTransactionStatus.UNKNOWN;
        }
        String value = transactionId.toLowerCase(Locale.ROOT);
        if (value.contains("pending")) {
            return PaymentProviderTransactionStatus.PENDING;
        }
        if (value.contains("fail")) {
            return PaymentProviderTransactionStatus.FAILED;
        }
        if (value.startsWith("sim-tx-")) {
            return PaymentProviderTransactionStatus.SUCCEEDED;
        }
        return PaymentProviderTransactionStatus.UNKNOWN;
    }
}
