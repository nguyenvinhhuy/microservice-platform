package huynv.paymentservice.config;

import huynv.paymentservice.exception.NonRetryableMessageException;
import huynv.paymentservice.exception.PaymentProcessingDisabledException;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.config.TopicBuilder;
import huynv.paymentservice.util.JitteredExponentialBackOff;
import org.springframework.kafka.listener.ListenerExecutionFailedException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

import java.util.HashMap;
import java.util.Map;

/**
 * Configures Kafka producer and consumer infrastructure including retry and dead-letter handling.
 */
@Configuration
public class KafkaConfig {

    /**
     * Creates a KafkaTemplate for publishing JSON strings to Kafka.
     *
     * @param bootstrapServers Kafka bootstrap servers.
     * @return Returns a KafkaTemplate configured with String serializers.
     */
    @Bean
    @Qualifier("paymentKafkaTemplate")
    public KafkaTemplate<String, String> paymentKafkaTemplate(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        return new KafkaTemplate<>(paymentProducerFactory(bootstrapServers));
    }

    /**
     * Creates a producer factory configured for idempotent String publishing.
     *
     * @param bootstrapServers Kafka bootstrap servers.
     * @return Returns a producer factory for String key and value publishing.
     */
    @Bean
    public ProducerFactory<String, String> paymentProducerFactory(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        props.put(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, "huynv.paymentservice.tracing.OtelKafkaProducerInterceptor");
        return new DefaultKafkaProducerFactory<>(props);
    }

    /**
     * Creates a consumer factory configured for consuming raw JSON strings.
     *
     * @param bootstrapServers Kafka bootstrap servers.
     * @param groupId Consumer group id used for payment-service listeners.
     * @return Returns a consumer factory for String key and value consumption.
     */
    @Bean
    public ConsumerFactory<String, String> paymentConsumerFactory(
            @Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @Value("${spring.kafka.consumer.group-id}") String groupId
    ) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.INTERCEPTOR_CLASSES_CONFIG, "huynv.paymentservice.tracing.OtelKafkaConsumerInterceptor");
        return new DefaultKafkaConsumerFactory<>(props);
    }

    /**
     * Creates a listener container factory with bounded retries and republish-to-retry and republish-to-dlq routing.
     *
     * @param consumerFactory Consumer factory used by the listener container.
     * @param kafkaTemplate Kafka template used for publishing failed records to retry and dead-letter topics.
     * @param meterRegistry Meter registry used to publish retry and dead-letter routing counters.
     * @param inventoryTopic Inbound inventory topic name used for the first-stage retry routing.
     * @param retryTopic Retry topic name used for second-stage processing.
     * @param dlqTopic Dead-letter topic name used for poison messages.
     * @return Returns a configured listener container factory.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> paymentKafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            @Qualifier("paymentKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            MeterRegistry meterRegistry,
            @Value("${payment.kafka.inventory-topic}") String inventoryTopic,
            @Value("${payment.kafka.retry-topic}") String retryTopic,
            @Value("${payment.kafka.dlq-topic}") String dlqTopic
    ) {
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);

        Counter retryTotal = meterRegistry.counter("kafka_consumer_retry_total", "service", "payment-service");
        Counter dlqTotal = meterRegistry.counter("kafka_consumer_dlq_total", "service", "payment-service");

        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate, (record, ex) -> {
            if (isNonRetryable(ex)) {
                dlqTotal.increment();
                return new TopicPartition(dlqTopic, record.partition());
            }
            String source = record.topic();
            if (inventoryTopic.equals(source)) {
                retryTotal.increment();
                return new TopicPartition(retryTopic, record.partition());
            }
            if (retryTopic.equals(source)) {
                dlqTotal.increment();
                return new TopicPartition(dlqTopic, record.partition());
            }
            dlqTotal.increment();
            return new TopicPartition(dlqTopic, record.partition());
        });

        JitteredExponentialBackOff backOff = new JitteredExponentialBackOff(500L, 2.0d, 30_000L, 0.2d, 5);
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);
        errorHandler.addNotRetryableExceptions(
                NonRetryableMessageException.class,
                PaymentProcessingDisabledException.class
        );
        factory.setCommonErrorHandler(errorHandler);
        return factory;
    }

    /**
     * Detects exceptions that should be sent directly to DLQ without topic-based retries.
     *
     * @param exception Listener exception raised by message processing.
     * @return Returns true when the exception is considered non-retryable for topic-based retry routing.
     */
    private static boolean isNonRetryable(Exception exception) {
        Throwable cursor = exception;
        while (cursor != null) {
            if (cursor instanceof NonRetryableMessageException || cursor instanceof PaymentProcessingDisabledException) {
                return true;
            }
            if (cursor instanceof ListenerExecutionFailedException failed && failed.getCause() != null) {
                cursor = failed.getCause();
                continue;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    /**
     * Declares the payment events topic for outbound publishing.
     *
     * @param eventsTopic Payment events topic name.
     * @return Returns a NewTopic definition for payment event publishing.
     */
    @Bean
    public NewTopic paymentEventsTopic(@Value("${payment.kafka.events-topic}") String eventsTopic) {
        return TopicBuilder.name(eventsTopic).partitions(3).replicas(1).build();
    }

    /**
     * Declares the payment events retry topic used for topic-based retry processing by downstream consumers.
     *
     * @param eventsTopic Payment events topic name used as the base for derived retry topic naming.
     * @return Returns a NewTopic definition for payment event retry publishing.
     */
    @Bean
    public NewTopic paymentEventsRetryTopic(@Value("${payment.kafka.events-topic}") String eventsTopic) {
        return TopicBuilder.name(eventsTopic + ".retry").partitions(3).replicas(1).build();
    }

    /**
     * Declares the payment events dead-letter topic used for poison message isolation by downstream consumers.
     *
     * @param eventsTopic Payment events topic name used as the base for derived dead-letter topic naming.
     * @return Returns a NewTopic definition for payment event dead-letter publishing.
     */
    @Bean
    public NewTopic paymentEventsDlqTopic(@Value("${payment.kafka.events-topic}") String eventsTopic) {
        return TopicBuilder.name(eventsTopic + ".dlq").partitions(3).replicas(1).build();
    }
}
