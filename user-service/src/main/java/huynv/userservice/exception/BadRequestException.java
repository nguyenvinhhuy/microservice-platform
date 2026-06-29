package huynv.userservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Indicates that the client submitted an invalid request.
 */
public class BadRequestException extends ApiException {

    /**
     * Creates a bad-request exception with a stable error code.
     *
     * @param errorCode Stable machine-readable error code.
     * @param message Human-readable error message.
     * @return Initializes a bad-request exception.
     */
    public BadRequestException(String errorCode, String message) {
        super(HttpStatus.BAD_REQUEST, errorCode, message);
    }
}

