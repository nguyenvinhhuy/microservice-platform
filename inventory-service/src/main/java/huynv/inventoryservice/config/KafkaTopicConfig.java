package huynv.inventoryservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String INVENTORY_EVENTS_TOPIC = "inventory.events";
    public static final String INVENTORY_EVENTS_RETRY_TOPIC = "inventory.events.retry";
    public static final String INVENTORY_EVENTS_DLQ_TOPIC = "inventory.events.dlq";

    /**
     * Declares the inventory events topic for local development and bootstrap environments.
     *
     * @return Kafka topic definition for inventory event publishing.
     */
    @Bean
    public NewTopic inventoryEventsTopic() {
        return TopicBuilder.name(INVENTORY_EVENTS_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Declares the inventory events retry topic used for topic-based retry processing.
     *
     * @return Returns a NewTopic definition for inventory event retry publishing.
     */
    @Bean
    public NewTopic inventoryEventsRetryTopic() {
        return TopicBuilder.name(INVENTORY_EVENTS_RETRY_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    /**
     * Declares the inventory events dead-letter topic used for poison message isolation.
     *
     * @return Returns a NewTopic definition for inventory event dead-letter publishing.
     */
    @Bean
    public NewTopic inventoryEventsDlqTopic() {
        return TopicBuilder.name(INVENTORY_EVENTS_DLQ_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
