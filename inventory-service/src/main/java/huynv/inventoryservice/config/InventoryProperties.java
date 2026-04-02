package huynv.inventoryservice.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Externalized configuration for inventory-related settings.
 * Allows tuning business logic parameters without changing code.
 */
@Configuration
@ConfigurationProperties(prefix = "inventory")
@Data
public class InventoryProperties {

    private Reservation reservation = new Reservation();

    @Data
    public static class Reservation {
        /**
         * The duration for which a stock reservation is held before it expires.
         * Format: ISO-8601 duration (e.g., PT10M for 10 minutes).
         */
        private Duration expiration = Duration.ofMinutes(10);

        /**
         * How often the scheduler runs to clean up expired reservations.
         * Value is in milliseconds.
         */
        private String expirationCheckInterval = "60000";
    }
}
