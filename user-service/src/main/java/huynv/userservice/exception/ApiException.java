package huynv.userservice.exception;

import org.springframework.http.HttpStatus;

import java.util.Objects;

/**
 * Represents a domain-aware application exception with an HTTP status and stable error code.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String errorCode;

    /**
     * Creates an application exception with a stable error code and HTTP status.
     *
     * @param status HTTP status to expose in the API response.
     * @param errorCode Stable machine-readable error code.
     * @param message Human-readable error message.
     * @return Initializes an application exception.
     */
    public ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = Objects.requireNonNull(status, "status");
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }

    /**
     * Returns the HTTP status associated with the exception.
     *
     * @return Returns the HTTP status associated with the exception.
     */
    public HttpStatus getStatus() {
        return status;
    }

    /**
     * Returns the stable machine-readable error code.
     *
     * @return Returns the stable machine-readable error code.
     */
    public String getErrorCode() {
        return errorCode;
    }
}

