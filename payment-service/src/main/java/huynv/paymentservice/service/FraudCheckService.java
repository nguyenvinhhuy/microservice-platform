package huynv.paymentservice.service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Defines a pluggable fraud check hook invoked before charging the payment provider.
 */
public interface FraudCheckService {

    /**
     * Performs a fraud check for the payment attempt and returns a decision.
     *
     * @param orderId Order identifier associated with the payment.
     * @param tenantId Tenant identifier for multi-tenant correlation when available.
     * @param amount Amount to be charged.
     * @param currency ISO currency code for the amount.
     * @return Returns a fraud check decision for the payment attempt.
     */
    FraudCheckDecision check(UUID orderId, Long tenantId, BigDecimal amount, String currency);
}

