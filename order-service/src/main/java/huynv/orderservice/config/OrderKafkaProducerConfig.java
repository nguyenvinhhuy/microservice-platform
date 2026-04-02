package huynv.orderservice.config;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class OrderKafkaProducerConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /**
     * Creates a Kafka producer factory for publishing order outbox messages.
     *
     * @return ProducerFactory configured for String keys and String values.
     */
    @Bean(name = "orderOutboxProducerFactory")
    public ProducerFactory<String, String> orderOutboxProducerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, "huynv.orderservice.tracing.OtelKafkaProducerInterceptor");
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * Creates a KafkaTemplate for publishing order outbox messages.
     *
     * @param producerFactory Producer factory used to create Kafka producers.
     * @return KafkaTemplate configured for String keys and String values.
     */
    @Bean(name = "orderOutboxKafkaTemplate")
    public KafkaTemplate<String, String> orderOutboxKafkaTemplate(
            ProducerFactory<String, String> producerFactory
    ) {
        return new KafkaTemplate<>(producerFactory);
    }
}
