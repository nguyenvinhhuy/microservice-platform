package huynv.paymentservice.controller;

import huynv.paymentservice.dto.ErrorResponse;
import huynv.paymentservice.exception.NonRetryableMessageException;
import huynv.paymentservice.exception.PaymentDomainException;
import huynv.paymentservice.exception.PaymentNotFoundException;
import huynv.paymentservice.exception.PaymentOptimisticLockException;
import huynv.paymentservice.exception.PaymentProviderTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.time.OffsetDateTime;
import java.util.stream.Collectors;

/**
 * Maps domain and validation exceptions to consistent HTTP responses.
 */
@RestControllerAdvice
public class PaymentExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(PaymentExceptionHandler.class);

    /**
     * Handles payment not found errors.
     *
     * @param exception Exception representing a missing payment.
     * @return Returns a 404 response with a standardized error body.
     */
    @ExceptionHandler(PaymentNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(PaymentNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(error("PAYMENT_NOT_FOUND", exception.getMessage()));
    }

    /**
     * Handles optimistic locking conflicts for concurrent payment mutations.
     *
     * @param exception Exception representing an optimistic lock conflict.
     * @return Returns a 409 response with a standardized error body.
     */
    @ExceptionHandler(PaymentOptimisticLockException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(PaymentOptimisticLockException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error("OPTIMISTIC_LOCK_CONFLICT", exception.getMessage()));
    }

    /**
     * Handles provider timeout errors reported by the payment provider client.
     *
     * @param exception Exception representing a provider timeout.
     * @return Returns a 504 response with a standardized error body.
     */
    @ExceptionHandler(PaymentProviderTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleProviderTimeout(PaymentProviderTimeoutException exception) {
        return ResponseEntity.status(HttpStatus.GATEWAY_TIMEOUT).body(error("PROVIDER_TIMEOUT", exception.getMessage()));
    }

    /**
     * Handles validation errors for request payloads.
     *
     * @param exception Exception representing validation failures.
     * @return Returns a 400 response with a standardized error body.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException exception) {
        String details = exception.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("VALIDATION_ERROR", details));
    }

    /**
     * Handles non-retryable message parsing and validation errors.
     *
     * @param exception Exception representing an invalid message.
     * @return Returns a 400 response with a standardized error body.
     */
    @ExceptionHandler(NonRetryableMessageException.class)
    public ResponseEntity<ErrorResponse> handleBadMessage(NonRetryableMessageException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("BAD_MESSAGE", exception.getMessage()));
    }

    /**
     * Handles domain errors that should be mapped to client-visible 400 responses.
     *
     * @param exception Exception representing a domain rule violation.
     * @return Returns a 400 response with a standardized error body.
     */
    @ExceptionHandler(PaymentDomainException.class)
    public ResponseEntity<ErrorResponse> handleDomain(PaymentDomainException exception) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(error("DOMAIN_ERROR", exception.getMessage()));
    }

    /**
     * Handles unexpected errors and logs them for diagnostics.
     *
     * @param exception Exception representing an unexpected server error.
     * @return Returns a 500 response with a standardized error body.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnexpected(Exception exception) {
        log.error("Unexpected payment-service error.", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error("INTERNAL_ERROR", "Unexpected error."));
    }

    /**
     * Builds a standardized ErrorResponse payload.
     *
     * @param code Stable error code for programmatic handling.
     * @param message Human-readable error message.
     * @return Returns a standardized error response payload.
     */
    private static ErrorResponse error(String code, String message) {
        return new ErrorResponse(code, message, OffsetDateTime.now());
    }
}
