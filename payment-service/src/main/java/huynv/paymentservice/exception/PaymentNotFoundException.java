package huynv.paymentservice.exception;

import java.util.UUID;

/**
 * Indicates a requested payment entity does not exist in the payment service database.
 */
public class PaymentNotFoundException extends PaymentDomainException {

    /**
     * Creates a not-found exception for a given payment identifier.
     *
     * @param paymentId Payment identifier that was not found.
     * @return Constructs an exception representing a missing payment.
     */
    public PaymentNotFoundException(UUID paymentId) {
        super("Payment not found: " + paymentId);
    }
}
