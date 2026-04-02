package huynv.paymentservice.service;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Provides a default fraud check implementation that approves all payments.
 */
@Component
public class AllowAllFraudCheckService implements FraudCheckService {

    /**
     * Performs a fraud check decision that always approves.
     *
     * @param orderId Order identifier associated with the payment.
     * @param tenantId Tenant identifier for multi-tenant correlation when available.
     * @param amount Amount to be charged.
     * @param currency ISO currency code for the amount.
     * @return Returns APPROVE to allow the payment to continue.
     */
    @Override
    public FraudCheckDecision check(UUID orderId, Long tenantId, BigDecimal amount, String currency) {
        return FraudCheckDecision.APPROVE;
    }
}

