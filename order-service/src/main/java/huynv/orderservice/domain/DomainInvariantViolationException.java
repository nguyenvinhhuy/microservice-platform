package huynv.orderservice.domain;

public class DomainInvariantViolationException extends RuntimeException {

    /**
     * DomainInvariantViolationException operation.
     *
     * @param message input parameter
     * @return performs side effects defined by this operation
     */
    public DomainInvariantViolationException(String message) {
        super(message);
    }
}
