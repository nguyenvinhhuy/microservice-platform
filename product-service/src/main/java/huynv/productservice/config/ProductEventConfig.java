package huynv.productservice.config;

import huynv.event.EventFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures event factory used to build unified Kafka envelopes for product-service events.
 */
@Configuration
public class ProductEventConfig {

    /**
     * Creates an event factory used to build unified Kafka envelopes.
     *
     * @return Returns an EventFactory configured with the service source name.
     */
    @Bean
    public EventFactory productEventFactory() {
        return new EventFactory("product-service", () -> MDC.get("traceId"));
    }
}

