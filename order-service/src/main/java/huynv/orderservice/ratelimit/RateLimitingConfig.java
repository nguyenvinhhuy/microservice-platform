package huynv.orderservice.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Enables configuration properties used by rate limiting components.
 */
@Configuration
@EnableConfigurationProperties(OrderRateLimitingProperties.class)
public class RateLimitingConfig {

    /**
     * Creates a configuration class instance for enabling rate limiting properties binding.
     *
     * @return Initializes the rate limiting configuration.
     */
    public RateLimitingConfig() {
    }
}

