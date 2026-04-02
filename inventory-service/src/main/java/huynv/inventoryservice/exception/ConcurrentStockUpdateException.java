package huynv.inventoryservice.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Custom exception thrown when an optimistic locking conflict occurs during a stock update.
 * This indicates that the data was modified by another transaction after it was read.
 * The client should be advised to retry the operation.
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class ConcurrentStockUpdateException extends RuntimeException {
    /**
     * Creates conflict exception for optimistic locking race during stock mutation.
     *
     * @param message conflict summary returned to caller
     * @param cause root cause from optimistic locking layer
     * @return performs side effects defined by this operation
     */
    public ConcurrentStockUpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}
