package huynv.fileservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Signals that the client submitted an invalid request.
 */
public class BadRequestException extends ApiException {

    /**
     * Creates a bad-request exception with a stable error code and detail message.
     *
     * @param errorCode Stable machine-readable error code.
     * @param message Human-readable error detail.
     * @return Initializes a bad-request exception.
     */
    public BadRequestException(String errorCode, String message) {
        super(HttpStatus.BAD_REQUEST, errorCode, message);
    }
}

