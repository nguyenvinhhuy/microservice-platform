package huynv.notificationservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.event.idempotency.IdempotencyService;
import huynv.notificationservice.repository.NotificationHistoryRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;

/**
 * Verifies that failed notifications are retried and eventually published to the DLQ topic.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = { "order.events", "payment.events", "notification.events.dlq" })
@TestPropertySource(properties = {
         "notification.email.smtp-enabled=true",
        "notification.channels.email-enabled=true",
        "notification.outbox.publisher.enabled=true",
        "notification.outbox.publisher.fixed-delay-ms=100",
        "notification.outbox.publisher.batch-size=1000",
        "notification.outbox.publisher.send-timeout-ms=5000"
})
public class DlqHandlingIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private NotificationHistoryRepository historyRepository;

    @MockitoBean
    private JavaMailSender mailSender;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    private Consumer<String, String> dlqConsumer;

    /**
     * Closes the DLQ consumer after each test to release Kafka resources.
     *
     * @return Performs side effects by closing the consumer.
     */
    @AfterEach
    public void tearDown() {
        if (dlqConsumer != null) {
            dlqConsumer.close();
        }
    }

    /**
     * Publishes a message that consistently fails delivery and asserts it is routed to DLQ after retries.
     *
     * @return Performs assertions against DLQ headers, idempotency state, and history persistence.
     */
    @Test
    public void shouldPublishToDlqAfterRetriesExceeded() throws Exception {
        doThrow(new RuntimeException("smtp-failure")).when(mailSender).send(any(SimpleMailMessage.class));

        MessageListenerContainer listener = kafkaListenerEndpointRegistry.getListenerContainer("notification-order-consumer");
        if (listener == null) {
            throw new IllegalStateException("Kafka listener container notification-order-consumer was not registered.");
        }
        ContainerTestUtils.waitForAssignment(listener, embeddedKafkaBroker.getPartitionsPerTopic());

        String eventId = "evt-dlq-" + UUID.randomUUID();
        kafkaTemplate.send("order.events", "k1", objectMapper.writeValueAsString(orderCreatedEnvelope(eventId))).get();

        dlqConsumer = createDlqConsumer();
        dlqConsumer.subscribe(java.util.List.of("notification.events.dlq"));

        ConsumerRecords<String, String> records = ConsumerRecords.empty();
        long deadline = System.currentTimeMillis() + 30_000L;
        while (System.currentTimeMillis() < deadline && records.isEmpty()) {
            records = dlqConsumer.poll(Duration.ofMillis(250));
        }

        ConsumerRecord<String, String> matched = null;
        for (ConsumerRecord<String, String> record : records) {
            if (record.value() != null && record.value().contains(eventId)) {
                matched = record;
                break;
            }
        }

        assertThat(matched).isNotNull();
        assertThat(headerValue(matched, "exception_class")).isNotBlank();
        assertThat(headerValue(matched, "exception_message")).isNotBlank();
        assertThat(headerValue(matched, "stack_hash")).isNotBlank();
        assertThat(headerValue(matched, "original_topic")).isEqualTo("order.events");
        assertThat(headerValue(matched, "consumer_group")).isEqualTo("notification-service-test");

        assertThat(idempotencyService.alreadyProcessed("1:" + eventId)).isFalse();
        assertThat(historyRepository.count()).isGreaterThanOrEqualTo(1L);
    }

    /**
     * Creates a Kafka consumer for reading from the DLQ topic in embedded Kafka tests.
     *
     * @return Returns a configured Consumer instance for reading DLQ records.
     */
    private Consumer<String, String> createDlqConsumer() {
        Map<String, Object> props = KafkaTestUtils.consumerProps("notification-dlq-test", "false", embeddedKafkaBroker);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        return new org.springframework.kafka.core.DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
    }

    /**
     * Reads a Kafka header value as UTF-8 text.
     *
     * @param record Kafka record containing headers.
     * @param key Header key to resolve.
     * @return Returns the header value as text or null when missing.
     */
    private static String headerValue(ConsumerRecord<String, String> record, String key) {
        if (record == null || key == null) {
            return null;
        }
        Header header = record.headers().lastHeader(key);
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }

    /**
     * Builds a minimal BaseEvent envelope map for an order.created event.
     *
     * @param eventId Event identifier used for idempotency verification.
     * @return Returns a map representing a JSON-serializable order.created event envelope.
     */
    private Map<String, Object> orderCreatedEnvelope(String eventId) {
        Map<String, Object> envelope = new HashMap<>();
        envelope.put("eventId", eventId);
        envelope.put("eventType", "order.created");
        envelope.put("source", "order-service");
        envelope.put("eventTime", Instant.now().toString());
        envelope.put("aggregateId", "order-1");
        envelope.put("aggregateVersion", 0);
        envelope.put("dataSchema", "order.created.v1");
        envelope.put("traceId", "trace-test");
        envelope.put("correlationId", "corr-test");
        envelope.put("causationId", null);
        envelope.put("data", Map.of(
                "orderId", "b9f9b2b3-2d1a-4e6b-8f9a-49b46cbca000",
                "tenantId", 1,
                "userId", 10,
                "status", "CREATED",
                "totalAmount", "12.34",
                "currency", "USD",
                "timestamp", Instant.now().toString()
        ));
        return envelope;
    }
}

