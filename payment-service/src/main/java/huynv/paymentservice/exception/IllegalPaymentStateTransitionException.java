package huynv.paymentservice.exception;

import huynv.paymentservice.domain.PaymentStatus;

/**
 * Signals an invalid payment status transition requested by application logic.
 */
public class IllegalPaymentStateTransitionException extends PaymentDomainException {

     /**
     * Creates an exception describing the attempted illegal transition.
     *
     * @param current Current payment status before transition.
     * @param target Target payment status requested by application logic.
     * @return Constructs an exception that indicates an invalid state transition.
     */
    public IllegalPaymentStateTransitionException(PaymentStatus current, PaymentStatus target) {
        super("Illegal payment status transition: " + current + " -> " + target);
    }
}
