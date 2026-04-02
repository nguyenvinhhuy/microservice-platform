package huynv.eventinfra.config;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * Validates critical notification-service configuration values at startup to fail fast on unsafe settings.
 */
@Component
public class NotificationStartupValidator {

    private final NotificationProperties notificationProperties;
    private final ProviderProperties providerProperties;

    /**
     * Creates a validator that checks critical notification-service configuration at startup.
     *
     * @param notificationProperties Notification configuration properties.
     * @param providerProperties Provider timeout configuration properties.
     * @return Initializes a notification startup validator.
     */
    public NotificationStartupValidator(NotificationProperties notificationProperties, ProviderProperties providerProperties) {
        this.notificationProperties = Objects.requireNonNull(notificationProperties, "notificationProperties");
        this.providerProperties = Objects.requireNonNull(providerProperties, "providerProperties");
    }

    /**
     * Validates configuration values and throws an exception when unsafe values are detected.
     *
     * @return Performs side effects by failing application startup when validation fails.
     */
    @PostConstruct
    public void validate() {
        if (notificationProperties.getDlq().getMaxReplayAttempts() <= 0) {
            throw new IllegalStateException("notification.dlq.maxReplayAttempts must be greater than 0.");
        }
        requirePositive(providerProperties.getEmail().getTimeout(), "provider.email.timeout");
        requirePositive(providerProperties.getSms().getTimeout(), "provider.sms.timeout");
        requirePositive(providerProperties.getPush().getTimeout(), "provider.push.timeout");

        NotificationProperties.Workers workers = notificationProperties.getWorkers();
        if (workers.getEmailPoolSize() <= 0 || workers.getSmsPoolSize() <= 0 || workers.getPushPoolSize() <= 0) {
            throw new IllegalStateException("notification.workers.*PoolSize must be greater than 0.");
        }
        if (workers.getEmailQueueSize() <= 0 || workers.getSmsQueueSize() <= 0 || workers.getPushQueueSize() <= 0) {
            throw new IllegalStateException("notification.workers.*QueueSize must be greater than 0.");
        }

        NotificationProperties.Dispatcher.PriorityWeights weights = notificationProperties.getDispatcher().getPriorityWeights();
        if (weights.getHigh() <= 0 || weights.getNormal() <= 0 || weights.getLow() <= 0) {
            throw new IllegalStateException("notification.dispatcher.priority-weights must be greater than 0.");
        }

        NotificationProperties.RateLimits rateLimits = notificationProperties.getRateLimits();
        if (rateLimits.getEmailPerSecond() <= 0 || rateLimits.getSmsPerSecond() <= 0 || rateLimits.getPushPerSecond() <= 0) {
            throw new IllegalStateException("notification.rate-limits.*-per-second must be greater than 0.");
        }
        if (rateLimits.getEmailBurst() <= 0 || rateLimits.getSmsBurst() <= 0 || rateLimits.getPushBurst() <= 0) {
            throw new IllegalStateException("notification.rate-limits.*-burst must be greater than 0.");
        }
    }

    /**
     * Validates that a duration is present and greater than zero.
     *
     * @param duration Duration to validate.
     * @param propertyName Property name used for error reporting.
     * @return Performs side effects by throwing when the duration is invalid.
     */
    private static void requirePositive(Duration duration, String propertyName) {
        if (duration == null) {
            throw new IllegalStateException(propertyName + " must be configured.");
        }
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalStateException(propertyName + " must be greater than 0.");
        }
    }
}


