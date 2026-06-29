package huynv.fileservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Signals that the caller exceeded a distributed rate limit or abuse-protection threshold.
 */
public class TooManyRequestsException extends ApiException {

    /**
     * Creates a rate-limit exception with a stable error code and human-readable detail.
     *
     * @param errorCode Stable machine-readable error code.
     * @param message Human-readable error detail.
     * @return Initializes a 429 Too Many Requests exception.
     */
    public TooManyRequestsException(String errorCode, String message) {
        super(HttpStatus.TOO_MANY_REQUESTS, errorCode, message);
    }
}

