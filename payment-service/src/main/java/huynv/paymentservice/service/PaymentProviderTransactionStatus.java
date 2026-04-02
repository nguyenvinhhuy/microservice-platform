package huynv.paymentservice.service;

/**
 * Defines a provider transaction status used for reconciliation and timeout handling.
 */
public enum PaymentProviderTransactionStatus {
    PENDING,
    SUCCEEDED,
    FAILED,
    UNKNOWN
}

