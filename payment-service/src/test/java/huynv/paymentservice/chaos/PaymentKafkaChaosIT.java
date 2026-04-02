package huynv.paymentservice.chaos;

import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.event.BaseEvent;
import huynv.event.inventory.StockReservedEvent;
import huynv.paymentservice.repository.PaymentRepository;
import huynv.paymentservice.repository.ProcessedEventRepository;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises Kafka duplicate delivery and retry-to-DLQ routing using Testcontainers Kafka and PostgreSQL.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class PaymentKafkaChaosIT {

    private static final String INVENTORY_TOPIC = "inventory.events";
    private static final String INVENTORY_RETRY_TOPIC = "inventory.events.retry";
    private static final String INVENTORY_DLQ_TOPIC = "inventory.events.dlq";

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id", () -> "payment-service-it");

        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");

        registry.add("payment.kafka.consumer.enabled", () -> "true");
        registry.add("payment.kafka.inventory-topic", () -> INVENTORY_TOPIC);
        registry.add("payment.kafka.retry-topic", () -> INVENTORY_RETRY_TOPIC);
        registry.add("payment.kafka.dlq-topic", () -> INVENTORY_DLQ_TOPIC);
        registry.add("payment.processing.enabled", () -> "true");
        registry.add("payment.provider.simulated.enabled", () -> "true");
    }

    @BeforeAll
    static void createTopics() throws Exception {
        Map<String, Object> config = Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        try (AdminClient admin = AdminClient.create(config)) {
            admin.createTopics(List.of(
                    new NewTopic(INVENTORY_TOPIC, 3, (short) 1),
                    new NewTopic(INVENTORY_RETRY_TOPIC, 3, (short) 1),
                    new NewTopic(INVENTORY_DLQ_TOPIC, 3, (short) 1)
            )).all().get();
        }
    }

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ProcessedEventRepository processedEventRepository;

    @Test
    void shouldRouteMalformedJsonToDlq() throws Exception {
        send(INVENTORY_TOPIC, "order-1", "{ not-json");

        var consumerProps = KafkaTestUtils.consumerProps("dlq-consumer", "false", kafka.getBootstrapServers());
        consumerProps.put(org.apache.kafka.clients.consumer.ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        var consumer = new DefaultKafkaConsumerFactory<String, String>(
                consumerProps,
                new StringDeserializer(),
                new StringDeserializer()
        ).createConsumer();
        consumer.subscribe(List.of(INVENTORY_DLQ_TOPIC));

        var records = KafkaTestUtils.getRecords(consumer, Duration.ofSeconds(15));
        assertTrue(records.count() >= 1);
        consumer.close();
    }

    @Test
    void shouldProcessDuplicateDeliveryIdempotently() throws Exception {
        paymentRepository.deleteAll();
        processedEventRepository.deleteAll();

        UUID orderId = UUID.randomUUID();
        String eventId = "01JCR3W7X6Y8T8V5Q7P4A3B2C1";
        String aggregateId = "order-" + orderId;

        StockReservedEvent data = new StockReservedEvent(
                orderId,
                1L,
                BigDecimal.valueOf(10.0d),
                "USD",
                "SIMULATED",
                "idem-" + orderId,
                List.of(new StockReservedEvent.ReservedItem(1L, 1))
        );
        BaseEvent<StockReservedEvent> envelope = new BaseEvent<>(
                eventId,
                "inventory.stock.reserved",
                "inventory-service",
                Instant.now(),
                aggregateId,
                0L,
                "inventory.stock.reserved.v1",
                "trace-it",
                "corr-" + orderId,
                null,
                data
        );
        String payload = objectMapper.writeValueAsString(envelope);

        send(INVENTORY_TOPIC, aggregateId, payload);
        send(INVENTORY_TOPIC, aggregateId, payload);

        await(Duration.ofSeconds(20));

        assertEquals(1, processedEventRepository.count());
        assertEquals(1, paymentRepository.count());
    }

    /**
     * Restarts the Kafka broker container and verifies the payment consumer continues processing after recovery.
     *
     * @return No return; asserts a valid event is processed after the broker restart.
     */
    @Test
    void shouldRecoverAfterKafkaBrokerRestart() throws Exception {
        paymentRepository.deleteAll();
        processedEventRepository.deleteAll();

        kafka.getDockerClient().restartContainerCmd(kafka.getContainerId()).exec();
        await(Duration.ofSeconds(10));

        UUID orderId = UUID.randomUUID();
        String aggregateId = "order-" + orderId;
        StockReservedEvent data = new StockReservedEvent(
                orderId,
                1L,
                BigDecimal.valueOf(10.0d),
                "USD",
                "SIMULATED",
                "idem-" + orderId,
                List.of(new StockReservedEvent.ReservedItem(1L, 1))
        );
        BaseEvent<StockReservedEvent> envelope = new BaseEvent<>(
                "01JCR3W7X6Y8T8V5Q7P4A3B2C2",
                "inventory.stock.reserved",
                "inventory-service",
                Instant.now(),
                aggregateId,
                0L,
                "inventory.stock.reserved.v1",
                "trace-it",
                "corr-" + orderId,
                null,
                data
        );
        send(INVENTORY_TOPIC, aggregateId, objectMapper.writeValueAsString(envelope));
        await(Duration.ofSeconds(20));

        assertEquals(1, processedEventRepository.count());
        assertEquals(1, paymentRepository.count());
    }

    /**
     * Sends a raw string record to Kafka using a minimal producer configuration.
     *
     * @param topic Topic name to publish to.
     * @param key Record key used for partitioning.
     * @param value Record value to publish.
     * @return Publishes the record to Kafka and waits for broker acknowledgment.
     */
    private static void send(String topic, String key, String value) throws Exception {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, "true");
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(props)) {
            producer.send(new ProducerRecord<>(topic, key, value)).get();
        }
    }

    /**
     * Waits for asynchronous processing to complete within a bounded time window.
     *
     * @param timeout Maximum duration to wait.
     * @return Returns when the timeout elapses.
     */
    private static void await(Duration timeout) throws InterruptedException {
        Thread.sleep(timeout.toMillis());
    }
}

