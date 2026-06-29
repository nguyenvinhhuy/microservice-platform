package huynv.fileservice.exception;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Translates application exceptions into RFC7807 ProblemDetail responses.
 */
@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /**
     * Handles domain-aware application exceptions.
     *
     * @param exception Application exception raised by the service layer.
     * @param request Current HTTP request.
     * @return Returns a ProblemDetail response with a stable error code.
     */
    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ProblemDetail> handleApiException(ApiException exception, HttpServletRequest request) {
        return buildResponse(exception.getStatus(), exception.getErrorCode(), exception.getMessage(), request, null, exception);
    }

    /**
     * Handles bean-validation failures for request bodies.
     *
     * @param exception Validation exception raised by Spring MVC.
     * @param request Current HTTP request.
     * @return Returns a ProblemDetail response containing field-level validation errors.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ProblemDetail> handleMethodArgumentNotValidException(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<Map<String, String>> violations = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toViolation)
                .toList();
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The request body contains invalid data.", request, violations, exception);
    }

    /**
     * Handles validation failures for request parameters.
     *
     * @param exception Constraint violation exception raised during parameter binding.
     * @param request Current HTTP request.
     * @return Returns a ProblemDetail response containing validation details.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ProblemDetail> handleConstraintViolationException(ConstraintViolationException exception, HttpServletRequest request) {
        List<String> violations = exception.getConstraintViolations().stream()
                .map(violation -> violation.getPropertyPath() + ": " + violation.getMessage())
                .toList();
        return buildResponse(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "The request parameters are invalid.", request, violations, exception);
    }

    /**
     * Handles authorization failures raised by Spring Security.
     *
     * @param exception Access denied exception raised by Spring Security.
     * @param request Current HTTP request.
     * @return Returns a forbidden ProblemDetail response.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDeniedException(AccessDeniedException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "ACCESS_DENIED", "You are not allowed to perform this action.", request, null, exception);
    }

    /**
     * Handles authentication failures raised by Spring Security.
     *
     * @param exception Authentication exception raised by Spring Security.
     * @param request Current HTTP request.
     * @return Returns an unauthorized ProblemDetail response.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ProblemDetail> handleAuthenticationException(AuthenticationException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Authentication is required for this endpoint.", request, null, exception);
    }

    /**
     * Handles optimistic-locking conflicts raised during concurrent updates.
     *
     * @param exception Optimistic locking exception raised by the persistence layer.
     * @param request Current HTTP request.
     * @return Returns a conflict ProblemDetail response.
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLockingFailureException(OptimisticLockingFailureException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "OPTIMISTIC_LOCK_CONFLICT", "The resource was updated concurrently. Please retry the request.", request, null, exception);
    }

    /**
     * Handles data-integrity violations raised by unique indexes and relational constraints.
     *
     * @param exception Data-integrity violation raised by the persistence layer.
     * @param request Current HTTP request.
     * @return Returns a conflict ProblemDetail response.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ProblemDetail> handleDataIntegrityViolationException(DataIntegrityViolationException exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.CONFLICT, "DATA_INTEGRITY_CONFLICT", "The request conflicts with the current persisted state.", request, null, exception);
    }

    /**
     * Handles all remaining unexpected exceptions.
     *
     * @param exception Unexpected exception raised during request processing.
     * @param request Current HTTP request.
     * @return Returns an internal-server-error ProblemDetail response.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedException(Exception exception, HttpServletRequest request) {
        return buildResponse(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected error occurred while processing the request.", request, null, exception);
    }

    /**
     * Builds a consistent RFC7807 response with repository-standard metadata.
     *
     * @param status HTTP status for the response.
     * @param errorCode Stable machine-readable error code.
     * @param detail Human-readable error detail.
     * @param request Current HTTP request.
     * @param violations Optional validation details.
     * @param exception Exception used for logging.
     * @return Returns a ResponseEntity containing a ProblemDetail payload.
     */
    public ResponseEntity<ProblemDetail> buildResponse(
            HttpStatus status,
            String errorCode,
            String detail,
            HttpServletRequest request,
            Object violations,
            Exception exception
    ) {
        String traceId = currentTraceId();
        if (status.is5xxServerError()) {
            log.error("file-service request failed status={} code={} traceId={} path={} message={}", status.value(), errorCode, traceId, request.getRequestURI(), detail, exception);
        } else {
            log.warn("file-service request rejected status={} code={} traceId={} path={} message={}", status.value(), errorCode, traceId, request.getRequestURI(), detail);
        }
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(status, detail);
        problemDetail.setTitle(status.getReasonPhrase());
        problemDetail.setProperty("errorCode", errorCode);
        problemDetail.setProperty("traceId", traceId);
        problemDetail.setProperty("timestamp", Instant.now().toString());
        problemDetail.setProperty("path", request.getRequestURI());
        if (violations != null) {
            problemDetail.setProperty("violations", violations);
        }
        HttpHeaders headers = new HttpHeaders();
        headers.add("X-Trace-Id", traceId);
        return new ResponseEntity<>(problemDetail, headers, status);
    }

    /**
     * Converts a Spring field error into a stable validation violation map.
     *
     * @param error Spring field error produced during request-body validation.
     * @return Returns a map containing the field name and human-readable message.
     */
    private Map<String, String> toViolation(FieldError error) {
        Map<String, String> violation = new LinkedHashMap<>();
        violation.put("field", error.getField());
        violation.put("message", error.getDefaultMessage());
        return violation;
    }

    /**
     * Resolves the current OpenTelemetry trace identifier or generates a fallback identifier.
     *
     * @return Returns the current trace identifier when available, or a random UUID fallback.
     */
    private static String currentTraceId() {
        SpanContext spanContext = Span.current().getSpanContext();
        if (spanContext.isValid()) {
            return spanContext.getTraceId();
        }
        return UUID.randomUUID().toString();
    }
}

