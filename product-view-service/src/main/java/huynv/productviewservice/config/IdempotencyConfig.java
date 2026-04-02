package huynv.productviewservice.config;

import huynv.event.idempotency.IdempotencyService;
import huynv.event.idempotency.JdbcIdempotencyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Declares the processed-events idempotency service used by Kafka consumers.
 */
@Configuration
public class IdempotencyConfig {

    /**
     * Creates an idempotency service that records processed event ids in the local database.
     *
     * @param dataSource DataSource used to access the processed_events table.
     * @param consumerService Consumer service name used to scope processed event markers.
     * @return Returns a JDBC-backed idempotency service.
     */
    @Bean
    public IdempotencyService idempotencyService(
            DataSource dataSource,
            @Value("${spring.application.name:product-view-service}") String consumerService
    ) {
        return new JdbcIdempotencyService(dataSource, consumerService);
    }
}


