package huynv.paymentservice.util;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Provides deterministic backoff calculation helpers for retry scheduling.
 */
public final class BackoffUtil {

    /**
     * Prevents instantiation of a static utility type.
     *
     * @return Prevents instantiation and enforces static access only.
     */
    private BackoffUtil() {
    }

    /**
     * Calculates the next retry timestamp using exponential backoff with jitter.
     *
     * @param now Current timestamp used as the base for the next attempt time.
     * @param attempts Publish attempts already recorded for the work item.
     * @param baseDelay Base delay used for the first retry.
     * @param maxDelay Maximum delay cap for scheduling retries.
     * @param jitterFactor Randomization factor expressed as a fraction of the computed delay.
     * @return Returns a timestamp representing when the next attempt should occur.
     */
    public static OffsetDateTime nextAttemptAt(
            OffsetDateTime now,
            int attempts,
            Duration baseDelay,
            Duration maxDelay,
            double jitterFactor
    ) {
        int exponent = Math.max(attempts, 0);
        double multiplier = Math.pow(2.0d, exponent);
        Duration computed = Duration.ofMillis((long) (baseDelay.toMillis() * multiplier));
        Duration capped = computed.compareTo(maxDelay) > 0 ? maxDelay : computed;

        long jitterRangeMillis = (long) (capped.toMillis() * Math.max(0.0d, jitterFactor));
        long jitterMillis = jitterRangeMillis == 0
                ? 0
                : ThreadLocalRandom.current().nextLong(-jitterRangeMillis, jitterRangeMillis + 1);

        return now.plus(Duration.ofMillis(Math.max(0L, capped.toMillis() + jitterMillis)));
    }
}
