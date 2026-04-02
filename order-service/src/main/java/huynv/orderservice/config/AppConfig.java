package huynv.orderservice.config;

import huynv.event.EventFactory;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableAsync
@EnableScheduling
@EnableConfigurationProperties({InventoryClientProperties.class, PaymentClientProperties.class, ProductClientProperties.class})
public class AppConfig {

    /**
     * Creates an event factory used to build unified Kafka envelopes for order-service events.
     *
     * @return Returns an EventFactory configured with the service source name.
     */
    @Bean
    public EventFactory orderEventFactory() {
        return new EventFactory("order-service", () -> MDC.get("traceId"));
    }
}

