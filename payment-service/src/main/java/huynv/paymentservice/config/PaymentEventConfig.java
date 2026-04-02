package huynv.paymentservice.config;

import huynv.event.EventFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures event factory used to build unified Kafka envelopes for payment-service events.
 */
@Configuration
public class PaymentEventConfig {

    /**
     * Creates an event factory used to build unified Kafka envelopes.
     *
     * @return Returns an EventFactory configured with the service source name.
     */
    @Bean
    public EventFactory paymentEventFactory() {
        return new EventFactory("payment-service", () -> MDC.get("traceId"));
    }
}

