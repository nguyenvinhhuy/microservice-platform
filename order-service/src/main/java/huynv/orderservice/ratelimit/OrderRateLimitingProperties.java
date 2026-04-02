package huynv.orderservice.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Defines token-bucket rate limiting properties for order creation.
 */
@ConfigurationProperties(prefix = "rate-limiting.orders")
public class OrderRateLimitingProperties {

    private boolean enabled = true;
    private long capacity = 50;
    private long refillTokens = 50;
    private Duration refillPeriod = Duration.ofMinutes(1);

    /**
     * Returns whether rate limiting is enabled for order creation.
     *
     * @return Returns true when the rate limiting filter is enabled.
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Sets whether rate limiting is enabled for order creation.
     *
     * @param enabled Flag controlling whether rate limiting is enabled.
     * @return Updates the enabled flag for order creation rate limiting.
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Returns the token bucket capacity.
     *
     * @return Returns the maximum number of tokens in the bucket.
     */
    public long getCapacity() {
        return capacity;
    }

    /**
     * Sets the token bucket capacity.
     *
     * @param capacity Maximum number of tokens in the bucket.
     * @return Updates the token bucket capacity.
     */
    public void setCapacity(long capacity) {
        this.capacity = capacity;
    }

    /**
     * Returns the number of tokens refilled per period.
     *
     * @return Returns the refill token amount.
     */
    public long getRefillTokens() {
        return refillTokens;
    }

    /**
     * Sets the number of tokens refilled per period.
     *
     * @param refillTokens Tokens to add during each refill interval.
     * @return Updates the refill token amount.
     */
    public void setRefillTokens(long refillTokens) {
        this.refillTokens = refillTokens;
    }

    /**
     * Returns the refill period duration.
     *
     * @return Returns the refill period duration.
     */
    public Duration getRefillPeriod() {
        return refillPeriod;
    }

    /**
     * Sets the refill period duration.
     *
     * @param refillPeriod Duration between refills.
     * @return Updates the refill period duration.
     */
    public void setRefillPeriod(Duration refillPeriod) {
        this.refillPeriod = refillPeriod;
    }
}

