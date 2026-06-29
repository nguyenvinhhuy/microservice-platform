package huynv.userservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Indicates that the current caller is not allowed to access a resource or action.
 */
public class ForbiddenOperationException extends ApiException {

    /**
     * Creates a forbidden-operation exception with a stable error code.
     *
     * @param errorCode Stable machine-readable error code.
     * @param message Human-readable error message.
     * @return Initializes a forbidden-operation exception.
     */
    public ForbiddenOperationException(String errorCode, String message) {
        super(HttpStatus.FORBIDDEN, errorCode, message);
    }
}

