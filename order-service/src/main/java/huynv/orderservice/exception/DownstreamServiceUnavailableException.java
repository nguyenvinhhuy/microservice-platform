package huynv.orderservice.exception;

/**
 * Represents a downstream dependency outage or protection-triggered failure that should be mapped to HTTP 503.
 */
public class DownstreamServiceUnavailableException extends RuntimeException {

    /**
     * Creates a downstream service unavailable exception with a message and root cause.
     *
     * @param message Error message describing the downstream failure.
     * @param cause Root cause exception from the downstream call path.
     * @return Initializes an exception instance describing the downstream failure.
     */
    public DownstreamServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}

