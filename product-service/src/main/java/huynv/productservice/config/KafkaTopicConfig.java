package huynv.productservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String PRODUCT_EVENTS_TOPIC = "product.events";
    public static final String PRODUCT_EVENTS_RETRY_TOPIC = "product.events.retry";
    public static final String PRODUCT_EVENTS_DLQ_TOPIC = "product.events.dlq";

    /**
     * Declares the main product events topic.
     *
     * @return Returns a NewTopic definition for product event publishing.
     */
    @Bean
    public NewTopic productEventsTopic() {
        return TopicBuilder.name(PRODUCT_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Declares the product events retry topic used for topic-based retry processing.
     *
     * @return Returns a NewTopic definition for product event retry publishing.
     */
    @Bean
    public NewTopic productEventsRetryTopic() {
        return TopicBuilder.name(PRODUCT_EVENTS_RETRY_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Declares the product events dead-letter topic used for poison message isolation.
     *
     * @return Returns a NewTopic definition for product event dead-letter publishing.
     */
    @Bean
    public NewTopic productEventsDlqTopic() {
        return TopicBuilder.name(PRODUCT_EVENTS_DLQ_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
