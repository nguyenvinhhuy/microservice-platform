package huynv.eventinfra.config;

import huynv.event.idempotency.IdempotencyService;
import huynv.event.idempotency.JdbcIdempotencyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * Configures consumer-side idempotency for Kafka event processing.
 */
@Configuration
public class IdempotencyConfig {

    /**
     * Creates a JDBC-backed idempotency service for tracking processed event identifiers.
     *
     * @param dataSource DataSource used to query and insert processed event markers.
     * @param consumerService Service name recorded into processed_events consumer_service column.
     * @return Returns an IdempotencyService backed by the processed_events table.
     */
    @Bean
    public IdempotencyService idempotencyService(
            DataSource dataSource,
            @Value("${spring.application.name:notification-service}") String consumerService
    ) {
        Objects.requireNonNull(dataSource, "dataSource");
        return new JdbcIdempotencyService(dataSource, consumerService);
    }
}



