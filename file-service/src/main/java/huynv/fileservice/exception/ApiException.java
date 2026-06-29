package huynv.fileservice.exception;

import java.util.Objects;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Describes a domain-aware runtime exception that carries a stable HTTP status and machine-readable error code.
 */
@Getter
public class ApiException extends RuntimeException {

    /**
     * -- GETTER --
     *  Returns the HTTP status associated with this exception.
     *
     * @return Returns the HTTP status associated with this exception.
     */
    private final HttpStatus status;
    /**
     * -- GETTER --
     *  Returns the stable machine-readable error code associated with this exception.
     *
     * @return Returns the stable machine-readable error code associated with this exception.
     */
    private final String errorCode;

    /**
     * Creates a new API exception with a stable status, error code, and detail message.
     *
     * @param status HTTP status that should be returned to the client.
     * @param errorCode Stable machine-readable error code.
     * @param message Human-readable error detail.
     * @return Initializes a new API exception.
     */
    public ApiException(HttpStatus status, String errorCode, String message) {
        super(message);
        this.status = Objects.requireNonNull(status, "status");
        this.errorCode = Objects.requireNonNull(errorCode, "errorCode");
    }
}
