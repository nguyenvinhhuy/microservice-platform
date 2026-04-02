package huynv.eventinfra.retry;

import java.time.Duration;

/**
 * Resolves retry delays for the tiered topic-based retry pipeline.
 */
public class RetryBackoffPolicy {

    private final int maxRetryAttempts;

    /**
     * Creates a backoff policy for retry tier resolution.
     *
     * @param maxRetryAttempts Maximum number of retry attempts before DLQ.
     * @return Initializes a retry backoff policy.
     */
    public RetryBackoffPolicy(int maxRetryAttempts) {
        if (maxRetryAttempts < 0) {
            throw new IllegalArgumentException("maxRetryAttempts must be >= 0.");
        }
        this.maxRetryAttempts = maxRetryAttempts;
    }

    /**
     * Resolves the delay for the next attempt, or null when the retry budget is exhausted.
     *
     * @param retryAttempt Retry attempt number to schedule, starting at 1 for the first retry.
     * @return Returns the delay for the next attempt or null when exhausted.
     */
    public Duration nextDelay(int retryAttempt) {
        if (retryAttempt < 1) {
            return null;
        }
        if (retryAttempt > maxRetryAttempts) {
            return null;
        }
        if (retryAttempt == 1) {
            return Duration.ofMinutes(1);
        }
        if (retryAttempt == 2) {
            return Duration.ofMinutes(5);
        }
        if (retryAttempt == 3) {
            return Duration.ofMinutes(30);
        }
        if (retryAttempt <= maxRetryAttempts) {
            return Duration.ofMinutes(30);
        }
        return null;
    }

    /**
     * Returns the maximum attempt count used by this policy.
     *
     * @return Returns the max attempt count.
     */
    public int maxAttempts() {
        return maxRetryAttempts;
    }

    /**
     * Validates an attempt number and normalizes it to a safe value.
     *
     * @param attempt Attempt number candidate.
     * @return Returns a safe attempt number.
     */
    public int normalizeAttempt(Integer attempt) {
        if (attempt == null || attempt < 1) {
            return 1;
        }
        if (maxRetryAttempts <= 0) {
            return 1;
        }
        return Math.min(attempt, Math.max(1, maxRetryAttempts));
    }
}

