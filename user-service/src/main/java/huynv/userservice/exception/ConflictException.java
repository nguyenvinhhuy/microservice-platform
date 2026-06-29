package huynv.userservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Indicates that the requested mutation conflicts with current persisted state.
 */
public class ConflictException extends ApiException {

    /**
     * Creates a conflict exception with a stable error code.
     *
     * @param errorCode Stable machine-readable error code.
     * @param message Human-readable error message.
     * @return Initializes a conflict exception.
     */
    public ConflictException(String errorCode, String message) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }
}

