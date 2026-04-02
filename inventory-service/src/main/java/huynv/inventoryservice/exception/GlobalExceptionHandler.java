package huynv.inventoryservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Global exception handler to centralize error response generation.
 * Ensures consistent, structured error messages for all API clients.
 */
@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles optimistic locking failures from JPA.
     * This is a critical handler that catches concurrent access issues. It wraps the
     * generic JPA exception into a domain-specific one and delegates to its handler.
     *
     * @param ex      The caught ObjectOptimisticLockingFailureException.
     * @param request The current web request.
     * @return A ResponseEntity with a 409 CONFLICT status.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    /**
     * handleOptimisticLock operation.
     *
     * @param ex input parameter
     * @param request input parameter
     * @return handleOptimisticLock result
     */
    public ResponseEntity<Object> handleOptimisticLock(ObjectOptimisticLockingFailureException ex, WebRequest request) {
        log.warn("Optimistic locking failure: {}", ex.getMessage());
        return createErrorResponse(HttpStatus.CONFLICT, "Concurrent stock update detected. Please retry.", request);
    }

    /**
     * Handles the domain-specific concurrent update exception.
     * Returns a clear 409 CONFLICT response to the client, indicating that the
     * operation should be retried.
     *
     * @param ex      The caught ConcurrentStockUpdateException.
     * @param request The current web request.
     * @return A ResponseEntity with a 409 CONFLICT status.
     */
    @ExceptionHandler(ConcurrentStockUpdateException.class)
    /**
     * handleConcurrentStockUpdateException operation.
     *
     * @param ex input parameter
     * @param request input parameter
     * @return handleConcurrentStockUpdateException result
     */
    public ResponseEntity<Object> handleConcurrentStockUpdateException(ConcurrentStockUpdateException ex, WebRequest request) {
        log.warn("ConcurrentStockUpdateException: {}", ex.getMessage());
        return createErrorResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    @ExceptionHandler(InsufficientStockException.class)
    /**
     * handleInsufficientStockException operation.
     *
     * @param ex input parameter
     * @param request input parameter
     * @return handleInsufficientStockException result
     */
    public ResponseEntity<Object> handleInsufficientStockException(InsufficientStockException ex, WebRequest request) {
        log.warn("InsufficientStockException: {}", ex.getMessage());
        return createErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    /**
     * handleReservationNotFoundException operation.
     *
     * @param ex input parameter
     * @param request input parameter
     * @return handleReservationNotFoundException result
     */
    public ResponseEntity<Object> handleReservationNotFoundException(ReservationNotFoundException ex, WebRequest request) {
        log.warn("ReservationNotFoundException: {}", ex.getMessage());
        return createErrorResponse(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidReservationStatusException.class)
    /**
     * handleInvalidReservationStatusException operation.
     *
     * @param ex input parameter
     * @param request input parameter
     * @return handleInvalidReservationStatusException result
     */
    public ResponseEntity<Object> handleInvalidReservationStatusException(InvalidReservationStatusException ex, WebRequest request) {
        log.warn("InvalidReservationStatusException: {}", ex.getMessage());
        return createErrorResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(TenantOwnershipViolationException.class)
    /**
     * handleTenantOwnershipViolationException operation.
     *
     * @param ex input parameter
     * @param request input parameter
     * @return handleTenantOwnershipViolationException result
     */
    public ResponseEntity<Object> handleTenantOwnershipViolationException(TenantOwnershipViolationException ex, WebRequest request) {
        log.warn("TenantOwnershipViolationException: {}", ex.getMessage());
        return createErrorResponse(HttpStatus.FORBIDDEN, ex.getMessage(), request);
    }

    /**
     * createErrorResponse operation.
     *
     * @param status input parameter
     * @param message input parameter
     * @param request input parameter
     * @return createErrorResponse result
     */
    private ResponseEntity<Object> createErrorResponse(HttpStatus status, String message, WebRequest request) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        body.put("path", request.getDescription(false));
        return new ResponseEntity<>(body, status);
    }
}
