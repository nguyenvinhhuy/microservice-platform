package huynv.eventinfra.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Defines alternative rate limit configuration keys used for operator-friendly overrides and backward compatibility.
 */
@Validated
@ConfigurationProperties(prefix = "notification.rate-limit")
public class NotificationRateLimitOverridesProperties {

    private final ChannelRateLimit email = new ChannelRateLimit();
    private final ChannelRateLimit sms = new ChannelRateLimit();
    private final ChannelRateLimit push = new ChannelRateLimit();

    /**
     * Returns email rate limit overrides when configured.
     *
     * @return Returns email rate limit overrides.
     */
    public ChannelRateLimit getEmail() {
        return email;
    }

    /**
     * Returns SMS rate limit overrides when configured.
     *
     * @return Returns SMS rate limit overrides.
     */
    public ChannelRateLimit getSms() {
        return sms;
    }

    /**
     * Returns push rate limit overrides when configured.
     *
     * @return Returns push rate limit overrides.
     */
    public ChannelRateLimit getPush() {
        return push;
    }

    /**
     * Defines a per-channel rate limit configuration using a token bucket model.
     */
    public static final class ChannelRateLimit {
        @Min(0)
        @Max(100000)
        private Integer perSecond;

        @Min(0)
        @Max(100000)
        private Integer burst;

        /**
         * Returns the configured per-second refill rate or null when not overridden.
         *
         * @return Returns the per-second rate limit override.
         */
        public Integer getPerSecond() {
            return perSecond;
        }

        /**
         * Sets the per-second refill rate override.
         *
         * @param perSecond Per-second rate limit override value.
         * @return Updates the per-second rate limit override configuration.
         */
        public void setPerSecond(Integer perSecond) {
            this.perSecond = perSecond;
        }

        /**
         * Returns the configured burst capacity or null when not overridden.
         *
         * @return Returns the burst capacity override.
         */
        public Integer getBurst() {
            return burst;
        }

        /**
         * Sets the burst capacity override.
         *
         * @param burst Burst capacity override value.
         * @return Updates the burst capacity override configuration.
         */
        public void setBurst(Integer burst) {
            this.burst = burst;
        }
    }
}

