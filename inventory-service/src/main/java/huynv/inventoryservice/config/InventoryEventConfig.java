package huynv.inventoryservice.config;

import huynv.event.EventFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures event factory used to build unified Kafka envelopes for inventory-service events.
 */
@Configuration
public class InventoryEventConfig {

    /**
     * Creates an event factory used to build unified Kafka envelopes.
     *
     * @return Returns an EventFactory configured with the service source name.
     */
    @Bean
    public EventFactory inventoryEventFactory() {
        return new EventFactory("inventory-service", () -> MDC.get("traceId"));
    }
}

