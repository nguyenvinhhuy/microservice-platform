package huynv.orderservice.exception;

import huynv.orderservice.domain.DomainInvariantViolationException;
import huynv.orderservice.dto.ErrorResponse;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import java.time.OffsetDateTime;

@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Maps downstream dependency outages to HTTP 503 for client-side retry behavior.
     *
     * @param ex Exception describing the downstream service failure.
     * @param request Web request used to populate response path metadata.
     * @return Returns an ErrorResponse with HTTP 503 and retryable=true.
     */
    @ExceptionHandler(DownstreamServiceUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleDownstreamUnavailable(DownstreamServiceUnavailableException ex, WebRequest request) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), true, request);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    /**
     * handleOrderNotFound operation.
     *
     * @param ex input parameter
     * @param request input parameter
     * @return handleOrderNotFound result
     */
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException ex, WebRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), false, request);
    }

    @ExceptionHandler(InvalidOrderStateException.class)
    /**
     * handleInvalidState operation.
     *
     * @param ex input parameter
     * @param request input parameter
     * @return handleInvalidState result
     */
    public ResponseEntity<ErrorResponse> handleInvalidState(InvalidOrderStateException ex, WebRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), true, request);
    }

    @ExceptionHandler(DomainInvariantViolationException.class)
    /**
     * handleDomainInvariant operation.
     *
     * @param ex input parameter
     * @param request input parameter
     * @return handleDomainInvariant result
     */
    public ResponseEntity<ErrorResponse> handleDomainInvariant(DomainInvariantViolationException ex, WebRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), true, request);
    }

    @ExceptionHandler(InventoryReservationFailedException.class)
    /**
     * handleInventoryFailure operation.
     *
     * @param ex input parameter
     * @param request input parameter
     * @return handleInventoryFailure result
     */
    public ResponseEntity<ErrorResponse> handleInventoryFailure(InventoryReservationFailedException ex, WebRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), true, request);
    }

    @ExceptionHandler(PaymentFailedException.class)
    /**
     * handlePaymentFailed operation.
     *
     * @param ex input parameter
     * @param request input parameter
     * @return handlePaymentFailed result
     */
    public ResponseEntity<ErrorResponse> handlePaymentFailed(PaymentFailedException ex, WebRequest request) {
        return build(HttpStatus.PAYMENT_REQUIRED, ex.getMessage(), false, request);
    }

    @ExceptionHandler({ObjectOptimisticLockingFailureException.class, RetryableConflictException.class})
    /**
     * handleOptimistic operation.
     *
     * @param ex input parameter
     * @param request input parameter
     * @return handleOptimistic result
     */
    public ResponseEntity<ErrorResponse> handleOptimistic(RuntimeException ex, WebRequest request) {
        return build(HttpStatus.CONFLICT, "Concurrent modification detected. Please retry.", true, request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    /**
     * handleValidation operation.
     *
     * @param ex input parameter
     * @param request input parameter
     * @return handleValidation result
     */
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex, WebRequest request) {
        String message = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(err -> err.getField() + " " + err.getDefaultMessage())
                .orElse("Invalid request");
        return build(HttpStatus.BAD_REQUEST, message, false, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    /**
     * handleConstraintViolation operation.
     *
     * @param ex input parameter
     * @param request input parameter
     * @return handleConstraintViolation result
     */
    public ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), false, request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    /**
     * handleIllegalArgument operation.
     *
     * @param ex input parameter
     * @param request input parameter
     * @return handleIllegalArgument result
     */
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex, WebRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), false, request);
    }

    @ExceptionHandler(Exception.class)
    /**
     * handleGeneric operation.
     *
     * @param ex input parameter
     * @param request input parameter
     * @return handleGeneric result
     */
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, WebRequest request) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, ex.getMessage(), false, request);
    }

    /**
     * build operation.
     *
     * @param status input parameter
     * @param message input parameter
     * @param retryable input parameter
     * @param request input parameter
     * @return build result
     */
    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, boolean retryable, WebRequest request) {
        ErrorResponse body = ErrorResponse.builder()
                .timestamp(OffsetDateTime.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(request.getDescription(false))
                .retryable(retryable)
                .build();
        return ResponseEntity.status(status).body(body);
    }
}
