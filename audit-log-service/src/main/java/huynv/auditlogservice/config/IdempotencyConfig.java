package huynv.auditlogservice.config;

import huynv.event.idempotency.IdempotencyService;
import huynv.event.idempotency.JdbcIdempotencyService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * Declares the idempotency service bean used by Kafka consumers to skip duplicate event deliveries.
 */
@Configuration
public class IdempotencyConfig {

    /**
     * Creates a JDBC-backed idempotency service scoped to the audit-log-service consumer.
     *
     * @param dataSource DataSource used to read and write processed_events rows.
     * @param consumerService Consumer service name recorded as the scope in processed_events rows.
     * @return Returns a JdbcIdempotencyService instance configured for the audit log consumer.
     */
    @Bean
    public IdempotencyService idempotencyService(
            DataSource dataSource,
            @Value("${spring.application.name:audit-log-service}") String consumerService
    ) {
        return new JdbcIdempotencyService(dataSource, consumerService);
    }
}

