package huynv.productservice.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Configures a dedicated KafkaTemplate for outbox publishing with String serializers.
 */
@Configuration
public class ProductKafkaProducerConfig {

    /**
     * Creates a producer factory configured for idempotent String publishing.
     *
     * @param bootstrapServers Kafka bootstrap servers.
     * @return Returns a producer factory for String key and value publishing.
     */
    @Bean
    @Qualifier("productOutboxProducerFactory")
    public ProducerFactory<String, String> productOutboxProducerFactory(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, "huynv.productservice.tracing.OtelKafkaProducerInterceptor");
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * Creates a KafkaTemplate dedicated to outbox publishing.
     *
     * @param producerFactory producer factory used to publish outbox payloads.
     * @return Returns a KafkaTemplate configured for String publishing.
     */
    @Bean
    @Qualifier("productOutboxKafkaTemplate")
    public KafkaTemplate<String, String> productOutboxKafkaTemplate(
            @Qualifier("productOutboxProducerFactory") ProducerFactory<String, String> producerFactory
    ) {
        return new KafkaTemplate<>(producerFactory);
    }
}
