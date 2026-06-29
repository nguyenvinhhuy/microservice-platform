package huynv.fileservice.exception;

import org.springframework.http.HttpStatus;

/**
 * Signals that a tenant attempted to exceed its allocated storage quota.
 */
public class QuotaExceededException extends ApiException {

    /**
     * Creates a quota-exceeded exception with a stable error code and detail message.
     *
     * @param errorCode Stable machine-readable error code.
     * @param message Human-readable error detail.
     * @return Initializes a quota-exceeded exception.
     */
    public QuotaExceededException(String errorCode, String message) {
        super(HttpStatus.CONFLICT, errorCode, message);
    }
}

