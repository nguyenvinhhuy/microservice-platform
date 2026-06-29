package huynv.fileservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Signals that the requested resource does not exist for the current tenant context.
 */
public class NotFoundException extends ApiException {

    /**
     * Creates a not-found exception with a stable error code and detail message.
     *
     * @param errorCode Stable machine-readable error code.
     * @param message Human-readable error detail.
     * @return Initializes a not-found exception.
     */
    public NotFoundException(String errorCode, String message) {
        super(HttpStatus.NOT_FOUND, errorCode, message);
    }
}

