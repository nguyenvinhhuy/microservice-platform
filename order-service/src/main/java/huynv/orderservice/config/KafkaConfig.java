package huynv.orderservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaConfig {

    public static final String ORDER_EVENTS_TOPIC = "order.events";
    public static final String ORDER_EVENTS_RETRY_TOPIC = "order.events.retry";
    public static final String ORDER_EVENTS_DLQ_TOPIC = "order.events.dlq";

    /**
     * Declares the main order events topic.
     *
     * @return Returns a NewTopic definition for order event publishing.
     */
    @Bean
    public NewTopic orderEventsTopic() {
        return TopicBuilder.name(ORDER_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Declares the order events retry topic used for topic-based retry processing.
     *
     * @return Returns a NewTopic definition for order event retry publishing.
     */
    @Bean
    public NewTopic orderEventsRetryTopic() {
        return TopicBuilder.name(ORDER_EVENTS_RETRY_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Declares the order events dead-letter topic used for poison message isolation.
     *
     * @return Returns a NewTopic definition for order event dead-letter publishing.
     */
    @Bean
    public NewTopic orderEventsDlqTopic() {
        return TopicBuilder.name(ORDER_EVENTS_DLQ_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
