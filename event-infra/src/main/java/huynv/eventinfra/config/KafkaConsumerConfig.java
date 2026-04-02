package huynv.eventinfra.config;

import huynv.eventinfra.exception.InvalidEventPayloadException;
import huynv.eventinfra.exception.NonRetryableNotificationException;
import huynv.eventinfra.metrics.NotificationMetrics;
import huynv.eventinfra.outbox.KafkaOutboxService;
import huynv.eventinfra.retry.KafkaOutboxRecoverer;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.ContainerProperties;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.FixedBackOff;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Objects;

/**
 * Configures Kafka consumption semantics including manual acknowledgment, retry, and DLQ publishing.
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    /**
     * Declares the notification-service dead-letter topic used for poison message isolation.
     *
     * @param properties Notification properties containing the DLQ topic name.
     * @return Returns a NewTopic definition for notification-service DLQ publishing.
     */
    @Bean
    public NewTopic notificationDlqTopic(NotificationProperties properties) {
        return TopicBuilder.name(properties.getKafka().getDlqTopic())
                .partitions(24)
                .replicas(1)
                .build();
    }

    /**
     * Declares the internal notification events topic used as a durable ingestion buffer.
     *
     * @param properties Notification properties containing internal topic names.
     * @return Returns a NewTopic definition for notification events.
     */
    @Bean
    public NewTopic notificationEventsTopic(NotificationProperties properties) {
        return TopicBuilder.name(properties.getKafka().getEventsTopic())
                .partitions(24)
                .replicas(1)
                .build();
    }

    /**
     * Declares the 1 minute retry tier topic used by the topic-based retry pipeline.
     *
     * @param properties Notification properties containing retry topic names.
     * @return Returns a NewTopic definition for the 1 minute retry topic.
     */
    @Bean
    public NewTopic notificationRetry1mTopic(NotificationProperties properties) {
        return TopicBuilder.name(properties.getKafka().getRetry1mTopic())
                .partitions(24)
                .replicas(1)
                .build();
    }

    /**
     * Declares the 5 minute retry tier topic used by the topic-based retry pipeline.
     *
     * @param properties Notification properties containing retry topic names.
     * @return Returns a NewTopic definition for the 5 minute retry topic.
     */
    @Bean
    public NewTopic notificationRetry5mTopic(NotificationProperties properties) {
        return TopicBuilder.name(properties.getKafka().getRetry5mTopic())
                .partitions(24)
                .replicas(1)
                .build();
    }

    /**
     * Declares the 30 minute retry tier topic used by the topic-based retry pipeline.
     *
     * @param properties Notification properties containing retry topic names.
     * @return Returns a NewTopic definition for the 30 minute retry topic.
     */
    @Bean
    public NewTopic notificationRetry30mTopic(NotificationProperties properties) {
        return TopicBuilder.name(properties.getKafka().getRetry30mTopic())
                .partitions(24)
                .replicas(1)
                .build();
    }

    /**
     * Declares the high priority notification job topic.
     *
     * @param properties Notification properties containing dispatcher topic names.
     * @return Returns a NewTopic definition for the high priority job topic.
     */
    @Bean
    public NewTopic notificationHighTopic(NotificationProperties properties) {
        return TopicBuilder.name(properties.getDispatcher().getHighTopic())
                .partitions(12)
                .replicas(1)
                .build();
    }

    /**
     * Declares the normal priority notification job topic.
     *
     * @param properties Notification properties containing dispatcher topic names.
     * @return Returns a NewTopic definition for the normal priority job topic.
     */
    @Bean
    public NewTopic notificationNormalTopic(NotificationProperties properties) {
        return TopicBuilder.name(properties.getDispatcher().getNormalTopic())
                .partitions(12)
                .replicas(1)
                .build();
    }

    /**
     * Declares the low priority notification job topic.
     *
     * @param properties Notification properties containing dispatcher topic names.
     * @return Returns a NewTopic definition for the low priority job topic.
     */
    @Bean
    public NewTopic notificationLowTopic(NotificationProperties properties) {
        return TopicBuilder.name(properties.getDispatcher().getLowTopic())
                .partitions(12)
                .replicas(1)
                .build();
    }

    /**
     * Declares the email worker topic used for channel-specific delivery execution.
     *
     * @param properties Notification properties containing dispatcher topic names.
     * @return Returns a NewTopic definition for the email worker topic.
     */
    @Bean
    public NewTopic notificationEmailTopic(NotificationProperties properties) {
        return TopicBuilder.name(properties.getDispatcher().getEmailTopic())
                .partitions(12)
                .replicas(1)
                .build();
    }

    /**
     * Declares the SMS worker topic used for channel-specific delivery execution.
     *
     * @param properties Notification properties containing dispatcher topic names.
     * @return Returns a NewTopic definition for the SMS worker topic.
     */
    @Bean
    public NewTopic notificationSmsTopic(NotificationProperties properties) {
        return TopicBuilder.name(properties.getDispatcher().getSmsTopic())
                .partitions(12)
                .replicas(1)
                .build();
    }

    /**
     * Declares the push worker topic used for channel-specific delivery execution.
     *
     * @param properties Notification properties containing dispatcher topic names.
     * @return Returns a NewTopic definition for the push worker topic.
     */
    @Bean
    public NewTopic notificationPushTopic(NotificationProperties properties) {
        return TopicBuilder.name(properties.getDispatcher().getPushTopic())
                .partitions(12)
                .replicas(1)
                .build();
    }

     /**
      * Builds the shared Kafka error handler with exponential backoff and DLQ publishing.
      *
      * @param properties Notification properties containing retry and DLQ configuration.
      * @param metrics Metrics used to track retry scheduling and DLQ publishing.
      * @param outboxService Outbox service used to persist retry and DLQ routing for asynchronous publishing.
      * @return Returns a CommonErrorHandler used by Kafka listener containers.
      */
    @Bean
    public CommonErrorHandler notificationKafkaErrorHandler(
            NotificationProperties properties,
            NotificationMetrics metrics,
            KafkaOutboxService outboxService
    ) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(metrics, "metrics");
        Objects.requireNonNull(outboxService, "outboxService");

        BackOff backOff = new FixedBackOff(0L, 0L);
        DefaultErrorHandler handler = new DefaultErrorHandler(new KafkaOutboxRecoverer(properties, outboxService, metrics), backOff);
        handler.addNotRetryableExceptions(InvalidEventPayloadException.class, NonRetryableNotificationException.class);
        handler.setCommitRecovered(true);
        handler.setRetryListeners((record, ex, deliveryAttempt) -> log.debug(
                "Kafka record recovered to retry pipeline attempt={} topic={} partition={} offset={} consumerGroup={} errorClass={} message={}",
                deliveryAttempt,
                record == null ? null : record.topic(),
                record == null ? null : record.partition(),
                record == null ? null : record.offset(),
                properties.getKafka().getGroupId(),
                ex == null ? null : ex.getClass().getName(),
                ex == null ? null : ex.getMessage()
        ));
        return handler;
    }

    /**
     * Configures the Kafka listener container factory with manual acknowledgments and the shared error handler.
     *
     * @param consumerFactory Consumer factory used by listener containers.
     * @param errorHandler Error handler used for retry and DLQ publishing.
     * @return Returns a Kafka listener container factory used by @KafkaListener methods.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, String> kafkaListenerContainerFactory(
            ConsumerFactory<String, String> consumerFactory,
            CommonErrorHandler errorHandler,
            NotificationProperties properties
    ) {
        Objects.requireNonNull(properties, "properties");
        ConcurrentKafkaListenerContainerFactory<String, String> factory = new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        factory.getContainerProperties().setAckMode(ContainerProperties.AckMode.MANUAL);
        factory.setCommonErrorHandler(errorHandler);
        factory.setConcurrency(properties.getKafka().getListenerConcurrency());
        factory.getContainerProperties().setShutdownTimeout(30_000L);
        return factory;
    }

    /**
     * Merges two header sets into one for use by DeadLetterPublishingRecoverer.
     *
     * @param first First header set.
     * @param second Second header set.
     * @return Returns merged headers.
     */
    private static Headers merge(Headers first, Headers second) {
        RecordHeaders merged = new RecordHeaders();
        if (first != null) {
            first.forEach(h -> merged.add(h.key(), h.value()));
        }
        if (second != null) {
            second.forEach(h -> merged.add(h.key(), h.value()));
        }
        return merged;
    }

    /**
     * Builds DLQ headers required for exception analysis and routing metadata.
     *
     * @param originalTopic Original topic where the record was consumed.
     * @param consumerGroup Consumer group that handled the record.
     * @param exception Exception that caused DLQ routing.
     * @return Returns a Headers instance containing the required DLQ header keys.
     */
    private static Headers buildDlqHeaders(org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record, String consumerGroup, Exception exception) {
        RecordHeaders headers = new RecordHeaders();
        headers.add("exception_class", toBytes(exception == null ? null : exception.getClass().getName()));
        headers.add("exception_message", toBytes(truncate(exception == null ? null : exception.getMessage(), 1024)));
        headers.add("stack_hash", toBytes(stackHash(exception)));
        headers.add("original_topic", toBytes(originalTopic(record)));
        headers.add("consumer_group", toBytes(consumerGroup));
        return headers;
    }

    /**
     * Resolves the logical original topic for DLQ headers using forwarded headers when available.
     *
     * @param record Consumer record that failed processing.
     * @return Returns the upstream original topic name when available, otherwise the record topic.
     */
    private static String originalTopic(org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record) {
        if (record == null) {
            return null;
        }
        org.apache.kafka.common.header.Header header = record.headers().lastHeader("original_topic");
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return record.topic();
    }

    /**
     * Computes a stable stack hash suitable for DLQ correlation.
     *
     * @param exception Exception to hash.
     * @return Returns a hex-encoded SHA-256 digest or null when exception is null.
     */
    private static String stackHash(Exception exception) {
        if (exception == null) {
            return null;
        }
        try {
            StringWriter writer = new StringWriter();
            exception.printStackTrace(new PrintWriter(writer));
            byte[] bytes = writer.toString().getBytes(StandardCharsets.UTF_8);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Truncates a string to a maximum length.
     *
     * @param value Value to truncate.
     * @param maxLength Maximum number of characters to keep.
     * @return Returns the truncated string or null when value is null.
     */
    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (maxLength <= 0) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    /**
     * Encodes a string value into UTF-8 bytes for Kafka header storage.
     *
     * @param value Header value.
     * @return Returns UTF-8 bytes or null when the value is null.
     */
    private static byte[] toBytes(String value) {
        if (value == null) {
            return null;
        }
        return value.getBytes(StandardCharsets.UTF_8);
    }
}

