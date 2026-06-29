package huynv.fileservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Signals that the request conflicts with the current persisted state.
 */
public class ConflictException extends ApiException {

    /**
     * Creates a conflict exception with a stable error code and detail message.
     *
     * @param errorCode Stable machine-readable error code.
     * @param message Human-readable error detail.
     * @return Initializes a conflict exception.
     */
    public ConflictException(String errorCode, String message) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }
}

