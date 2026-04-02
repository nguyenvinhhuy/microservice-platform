package huynv.inventoryservice.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * Creates a producer factory configured from application properties.
     *
     * @return Producer factory for publishing inventory events with String keys and JSON values.
     */
    @Bean
    @ConditionalOnMissingBean
    public ProducerFactory<String, Object> producerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, "huynv.inventoryservice.tracing.OtelKafkaProducerInterceptor");
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * Creates a KafkaTemplate for publishing inventory events.
     *
     * @param producerFactory Producer factory used to create Kafka producers.
     * @return KafkaTemplate used to publish inventory domain events to Kafka topics.
     */
    @Bean
    @ConditionalOnMissingBean
    public KafkaTemplate<String, Object> kafkaTemplate(ProducerFactory<String, Object> producerFactory) {
        return new KafkaTemplate<>(producerFactory);
    }

    /**
     * Creates a producer factory for outbox publishing of JSON strings.
     *
     * @return Producer factory configured for String keys and String values.
     */
    @Bean
    @Qualifier("inventoryOutboxProducerFactory")
    public ProducerFactory<String, String> inventoryOutboxProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, "huynv.inventoryservice.tracing.OtelKafkaProducerInterceptor");
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * Creates a KafkaTemplate dedicated to inventory outbox publishing with String serializers.
     *
     * @param producerFactory producer factory used for outbox publishing.
     * @return KafkaTemplate used by the outbox publisher worker.
     */
    @Bean
    @Qualifier("inventoryOutboxKafkaTemplate")
    public KafkaTemplate<String, String> inventoryOutboxKafkaTemplate(
            @Qualifier("inventoryOutboxProducerFactory") ProducerFactory<String, String> producerFactory
    ) {
        return new KafkaTemplate<>(producerFactory);
    }
}
