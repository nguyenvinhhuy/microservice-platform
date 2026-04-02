package huynv.eventinfra.outbox;

import huynv.eventinfra.config.NotificationProperties;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Computes exponential backoff with jitter for outbox publish retries to avoid retry storms during outages.
 */
public class KafkaOutboxPublishBackoffPolicy {

    private final NotificationProperties properties;
    private final double jitterFactor;

    /**
     * Creates a backoff policy using notification retry configuration.
     *
     * @param properties Notification properties providing initial interval, multiplier, and max interval.
     * @param jitterFactor Randomization factor in the range [0.0, 1.0] applied as +/- percentage to the base delay.
     * @return Initializes a Kafka outbox publish backoff policy.
     */
    public KafkaOutboxPublishBackoffPolicy(NotificationProperties properties, double jitterFactor) {
        this.properties = Objects.requireNonNull(properties, "properties");
        if (jitterFactor < 0.0 || jitterFactor > 1.0) {
            throw new IllegalArgumentException("jitterFactor must be between 0.0 and 1.0.");
        }
        this.jitterFactor = jitterFactor;
    }

    /**
     * Computes the next retry delay based on the current retry count.
     *
     * @param retryCount Current retry count persisted for the outbox row.
     * @return Returns a delay duration that includes exponential backoff and jitter.
     */
    public Duration nextDelay(int retryCount) {
        int safeRetry = Math.max(0, retryCount);
        NotificationProperties.Retry retry = properties.getRetry();
        long baseMs = retry.getInitialIntervalMs();
        double multiplier = retry.getMultiplier();
        long maxMs = retry.getMaxIntervalMs();

        double scaled = baseMs * Math.pow(multiplier, safeRetry);
        long capped = (long) Math.min(Math.max(1.0, scaled), (double) maxMs);
        long jittered = applyJitter(capped);
        return Duration.ofMillis(Math.max(1L, jittered));
    }

    private long applyJitter(long baseMs) {
        if (jitterFactor <= 0.0) {
            return baseMs;
        }
        double delta = baseMs * jitterFactor;
        double min = baseMs - delta;
        double max = baseMs + delta;
        double sampled = ThreadLocalRandom.current().nextDouble(min, max);
        return (long) Math.max(1.0, sampled);
    }
}


