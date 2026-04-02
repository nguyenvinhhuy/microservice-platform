package huynv.notificationservice.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.event.idempotency.IdempotencyService;
import huynv.notificationservice.repository.NotificationHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies end-to-end consumption of Kafka events and persistence of notification history and idempotency markers.
 */
@SpringBootTest
@ActiveProfiles("test")
@EmbeddedKafka(partitions = 1, topics = { "order.events", "payment.events", "notification.events.dlq" })
@TestPropertySource(properties = {
        "notification.outbox.publisher.enabled=true",
        "notification.outbox.publisher.fixed-delay-ms=100",
        "notification.outbox.publisher.batch-size=1000",
        "notification.outbox.publisher.send-timeout-ms=5000"
})
public class NotificationKafkaIntegrationTest {

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private NotificationHistoryRepository historyRepository;

    @Autowired
    private IdempotencyService idempotencyService;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    /**
     * Consumes an order.created event and persists notification history and processed marker exactly once.
     *
     * @return Performs assertions against the database and idempotency service.
     */
    @Test
    public void shouldConsumeOrderCreatedAndPersistHistory() throws Exception {
        MessageListenerContainer listener = kafkaListenerEndpointRegistry.getListenerContainer("notification-order-consumer");
        if (listener == null) {
            throw new IllegalStateException("Kafka listener container notification-order-consumer was not registered.");
        }
        ContainerTestUtils.waitForAssignment(listener, embeddedKafkaBroker.getPartitionsPerTopic());

        String eventId = "evt-order-" + UUID.randomUUID();
        String payload = objectMapper.writeValueAsString(orderCreatedEnvelope(eventId));

        kafkaTemplate.send("order.events", "k1", payload).get();

        await(() -> historyRepository.count() >= 1, 15_000L);
        assertThat(idempotencyService.alreadyProcessed(eventId)).isTrue();
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

    /**
     * Waits until a condition becomes true or a timeout elapses.
     *
     * @param condition Condition to evaluate until it is satisfied.
     * @param timeoutMs Timeout in milliseconds before failing the wait.
     * @return Performs a side effect by sleeping and polling until the condition is satisfied.
     */
    private static void await(Condition condition, long timeoutMs) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (condition.satisfied()) {
                return;
            }
            Thread.sleep(50L);
        }
        throw new AssertionError("Condition not satisfied within timeoutMs=" + timeoutMs + ".");
    }

    private interface Condition {
        /**
         * Returns whether the condition is satisfied.
         *
         * @return Returns true when the condition is satisfied.
         */
        boolean satisfied();
    }
}

