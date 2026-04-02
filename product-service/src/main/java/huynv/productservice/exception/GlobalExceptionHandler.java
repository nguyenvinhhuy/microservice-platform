package huynv.productservice.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@ControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidationExceptions(
            MethodArgumentNotValidException ex, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getBindingResult().getAllErrors().forEach(error -> {
            String fieldName = ((FieldError) error).getField();
            String errorMessage = error.getDefaultMessage();
            errors.put(fieldName, errorMessage);
        });
        String traceId = UUID.randomUUID().toString(); // Generate a unique traceId for this error
        log.warn("Validation failed for request {}. Errors: {}. TraceId: {}", request.getDescription(false), errors, traceId);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.BAD_REQUEST.value())
                .error(HttpStatus.BAD_REQUEST.getReasonPhrase())
                .message("Validation failed")
                .details(errors.toString())
                .path(request.getDescription(false).replace("uri=", ""))
                .traceId(traceId)
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(RuntimeException.class)
    /**
     * handleRuntimeException operation.
     *
     * @param ex input parameter
     * @param request input parameter
     * @return handleRuntimeException result
     */
    public ResponseEntity<ErrorResponse> handleRuntimeException(RuntimeException ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("Runtime exception occurred: {}. TraceId: {}", ex.getMessage(), traceId, ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .traceId(traceId)
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Custom exception for Quota Exceeded
    @ExceptionHandler(QuotaExceededException.class)
    /**
     * handleQuotaExceededException operation.
     *
     * @param ex input parameter
     * @param request input parameter
     * @return handleQuotaExceededException result
     */
    public ResponseEntity<ErrorResponse> handleQuotaExceededException(QuotaExceededException ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.warn("Quota exceeded: {}. TraceId: {}", ex.getMessage(), traceId);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error(HttpStatus.FORBIDDEN.getReasonPhrase())
                .message(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .traceId(traceId)
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    // Custom exception for Access Denied (RBAC)
    @ExceptionHandler(AccessDeniedException.class)
    /**
     * handleAccessDeniedException operation.
     *
     * @param ex input parameter
     * @param request input parameter
     * @return handleAccessDeniedException result
     */
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.warn("Access Denied: {}. TraceId: {}", ex.getMessage(), traceId);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.FORBIDDEN.value())
                .error(HttpStatus.FORBIDDEN.getReasonPhrase())
                .message("Access Denied: You do not have permission to perform this action.")
                .path(request.getDescription(false).replace("uri=", ""))
                .traceId(traceId)
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.FORBIDDEN);
    }

    // Generic exception handler
    @ExceptionHandler(Exception.class)
    /**
     * handleAllExceptions operation.
     *
     * @param ex input parameter
     * @param request input parameter
     * @return handleAllExceptions result
     */
    public ResponseEntity<ErrorResponse> handleAllExceptions(Exception ex, WebRequest request) {
        String traceId = UUID.randomUUID().toString();
        log.error("An unexpected error occurred: {}. TraceId: {}", ex.getMessage(), traceId, ex);

        ErrorResponse errorResponse = ErrorResponse.builder()
                .timestamp(Instant.now())
                .status(HttpStatus.INTERNAL_SERVER_ERROR.value())
                .error(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase())
                .message("An unexpected error occurred. Please try again later.")
                .details(ex.getMessage())
                .path(request.getDescription(false).replace("uri=", ""))
                .traceId(traceId)
                .build();
        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
