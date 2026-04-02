package huynv.paymentservice.util;

import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Provides an exponential backoff implementation with jitter and a bounded maximum number of retries.
 */
public final class JitteredExponentialBackOff implements BackOff {

    private final long initialIntervalMs;
    private final double multiplier;
    private final long maxIntervalMs;
    private final double randomizationFactor;
    private final int maxRetries;

    /**
     * Creates a jittered exponential backoff strategy.
     *
     * @param initialIntervalMs Initial retry interval in milliseconds.
     * @param multiplier Exponential multiplier applied per retry attempt.
     * @param maxIntervalMs Maximum retry interval cap in milliseconds.
     * @param randomizationFactor Jitter factor as a fraction of the computed interval.
     * @param maxRetries Maximum number of retries before giving up.
     * @return Initializes a jittered exponential backoff strategy.
     */
    public JitteredExponentialBackOff(
            long initialIntervalMs,
            double multiplier,
            long maxIntervalMs,
            double randomizationFactor,
            int maxRetries
    ) {
        this.initialIntervalMs = initialIntervalMs;
        this.multiplier = multiplier;
        this.maxIntervalMs = maxIntervalMs;
        this.randomizationFactor = randomizationFactor;
        this.maxRetries = maxRetries;
    }

    /**
     * Starts a new backoff execution for one retry sequence.
     *
     * @return Returns a BackOffExecution instance tracking retry attempts.
     */
    @Override
    public BackOffExecution start() {
        return new Execution();
    }

    private final class Execution implements BackOffExecution {
        private int attempts;

        @Override
        public long nextBackOff() {
            if (attempts >= maxRetries) {
                return BackOffExecution.STOP;
            }

            double exp = Math.pow(multiplier, attempts);
            long interval = (long) (initialIntervalMs * exp);
            long capped = Math.min(interval, maxIntervalMs);

            long jitterRange = (long) (capped * Math.max(0.0d, randomizationFactor));
            long jitter = jitterRange == 0 ? 0 : ThreadLocalRandom.current().nextLong(-jitterRange, jitterRange + 1);

            attempts++;
            long result = capped + jitter;
            return Math.max(0L, result);
        }
    }
}

