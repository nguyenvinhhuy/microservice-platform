package huynv.notificationservice.service.provider;

/**
 * Represents a provider-specific failure with retry classification.
 */
public class ProviderException extends RuntimeException {

    private final boolean retryable;

    /**
     * Creates a provider exception with retry classification.
     *
     * @param message Error message.
     * @param retryable Whether the error is retryable.
     * @return Initializes a provider exception.
     */
    public ProviderException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    /**
     * Creates a provider exception with retry classification and a root cause.
     *
     * @param message Error message.
     * @param retryable Whether the error is retryable.
     * @param cause Root cause of the failure.
     * @return Initializes a provider exception.
     */
    public ProviderException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    /**
     * Returns whether the provider error is retryable.
     *
     * @return Returns true when retryable.
     */
    public boolean isRetryable() {
        return retryable;
    }
}

