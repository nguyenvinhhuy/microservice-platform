package huynv.userservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Indicates that a requested tenant-scoped resource does not exist.
 */
public class ResourceNotFoundException extends ApiException {

    /**
     * Creates a resource-not-found exception with a stable error code.
     *
     * @param errorCode Stable machine-readable error code.
     * @param message Human-readable error message.
     * @return Initializes a resource-not-found exception.
     */
    public ResourceNotFoundException(String errorCode, String message) {
        super(HttpStatus.NOT_FOUND, errorCode, message);
    }
}

