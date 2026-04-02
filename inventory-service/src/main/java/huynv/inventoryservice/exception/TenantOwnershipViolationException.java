package huynv.inventoryservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.FORBIDDEN)
public class TenantOwnershipViolationException extends RuntimeException {
    /**
     * TenantOwnershipViolationException operation.
     *
     * @param message input parameter
     * @return performs side effects defined by this operation
     */
    public TenantOwnershipViolationException(String message) {
        super(message);
    }
}
