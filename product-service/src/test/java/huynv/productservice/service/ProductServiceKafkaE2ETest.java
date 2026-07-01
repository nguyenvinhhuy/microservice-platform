package huynv.productservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.productservice.config.KafkaTopicConfig;
import huynv.productservice.dto.ProductDTO;
import huynv.productservice.model.Product;
import huynv.productservice.model.ProductStatus;
import huynv.productservice.repository.ProductRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import huynv.productservice.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.TestPropertySource;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = {KafkaTopicConfig.PRODUCT_EVENTS_TOPIC})
@TestPropertySource(
        properties = {
            "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
            "spring.datasource.url=jdbc:h2:mem:e2etestdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;NON_KEYWORDS=VALUE",
            "spring.datasource.driver-class-name=org.h2.Driver",
            "spring.datasource.username=sa",
            "spring.datasource.password=",
            "spring.jpa.hibernate.ddl-auto=create-drop",
            "spring.sql.init.mode=never",
            "product.outbox.publisher-delay-ms=200",
            "spring.cloud.compatibility-verifier.enabled=false"
        })
class ProductServiceKafkaE2ETest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private Consumer<String, String> consumer;

    /**
     * Initializes test infrastructure before each test by creating the shedlock table if absent,
     * populating a thread-local UserContext with tenant and admin roles, purging all persisted
     * products, and subscribing a fresh Kafka consumer to the product events topic.
     *
     * @return Configures Kafka consumer, database state, and UserContext as side effects; no value is returned.
     */
    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
                "CREATE TABLE IF NOT EXISTS shedlock("
                        + "name VARCHAR(64) NOT NULL,"
                        + "lock_until TIMESTAMP(3) NOT NULL,"
                        + "locked_at TIMESTAMP(3) NOT NULL,"
                        + "locked_by VARCHAR(255) NOT NULL,"
                        + "PRIMARY KEY (name))");

        UserContext ctx = new UserContext();
        ctx.setTenantId(1L);
        ctx.setUserId(100L);
        ctx.setRoles(java.util.List.of("ROLE_ADMIN"));
        UserContext.setCurrentUserContext(ctx);

        productRepository.deleteAll();

        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, embeddedKafkaBroker.getBrokersAsString());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + System.currentTimeMillis());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        consumer = new DefaultKafkaConsumerFactory<String, String>(props).createConsumer();
        consumer.subscribe(Collections.singletonList(KafkaTopicConfig.PRODUCT_EVENTS_TOPIC));
        // Drain any records leftover from a previous test
        consumer.poll(Duration.ofMillis(500));
        consumer.commitSync();
    }

    /**
     * Releases test resources after each test by closing the Kafka consumer and clearing
     * the thread-local UserContext to prevent state leakage between tests.
     *
     * @return Releases Kafka consumer and UserContext as side effects; no value is returned.
     */
    @AfterEach
    void tearDown() {
        if (consumer != null) {
            consumer.close();
        }
        UserContext.clear();
    }

    /**
     * Verifies that updating a product's status from DRAFT to ACTIVE publishes a product.updated
     * event to the product events Kafka topic with correct envelope fields, aggregate key,
     * event type, source, schema version, and payload data.
     *
     * @return Asserts correct Kafka event publication and message content as side effects; no value is returned.
     * @throws Exception If Kafka consumer operations or JSON deserialization fail.
     */
    @Test
    void shouldPublishProductUpdatedEventWhenStatusChangesToActive() throws Exception {
        ProductDTO productDTO =
                ProductDTO.builder()
                        .code("P001")
                        .name("Test Product")
                        .slug("test-product")
                        .description("Description for test product")
                        .price(BigDecimal.valueOf(100.00))
                        .currency("USD")
                        .status(ProductStatus.DRAFT)
                        .categoryId(1L)
                        .thumbnailUrl("http://example.com/thumb.jpg")
                        .build();
        productService.createProduct(productDTO);

        Product savedProduct = productRepository.findAll().getFirst();

        ProductDTO updateDTO =
                ProductDTO.builder()
                        .id(savedProduct.getId())
                        .code(savedProduct.getCode())
                        .name(savedProduct.getName())
                        .slug(savedProduct.getSlug())
                        .description(savedProduct.getDescription())
                        .price(savedProduct.getPrice())
                        .currency(savedProduct.getCurrency())
                        .categoryId(savedProduct.getCategoryId())
                        .status(ProductStatus.ACTIVE)
                        .thumbnailUrl(savedProduct.getThumbnailUrl())
                        .build();
        productService.updateProduct(savedProduct.getId(), updateDTO);

        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

        assertFalse(records.isEmpty(), "Kafka record should not be empty");
        assertEquals(1, records.count(), "Should receive exactly one record");

        var record = records.iterator().next();
        String payload = record.value();
        JsonNode envelope = objectMapper.readTree(payload);

        assertNotNull(envelope.get("eventId").asText());
        assertEquals("product.updated", envelope.get("eventType").asText());
        assertEquals("product-" + savedProduct.getId(), envelope.get("aggregateId").asText());
        assertEquals(0L, envelope.get("aggregateVersion").asLong());
        assertFalse(envelope.get("eventTime").isNull());
        assertEquals("product-service", envelope.get("source").asText());
        assertEquals("product.updated.v1", envelope.get("dataSchema").asText());
        assertFalse(envelope.get("correlationId").isNull());

        JsonNode data = envelope.get("data");
        assertNotNull(data);
        assertEquals(savedProduct.getId().longValue(), data.get("productId").asLong());
        assertEquals(savedProduct.getCode(), data.get("code").asText());
        assertEquals(savedProduct.getName(), data.get("name").asText());
        assertEquals(
                savedProduct.getPrice().stripTrailingZeros(),
                new java.math.BigDecimal(data.get("price").asText()).stripTrailingZeros());
        assertEquals(savedProduct.getCurrency(), data.get("currency").asText());

        assertEquals("product-" + savedProduct.getId(), record.key());
    }

    /**
     * Verifies that updating a product to any status — not just ACTIVE — still publishes
     * a product.updated event, confirming that the outbox publisher fires unconditionally
     * on every successful update regardless of the target status.
     *
     * @return Asserts Kafka event publication for a non-ACTIVE status change as a side effect; no value is returned.
     * @throws Exception If Kafka consumer operations or JSON deserialization fail.
     */
    @Test
    void shouldPublishProductUpdatedEventForAnyStatusChange() throws Exception {
        ProductDTO productDTO =
                ProductDTO.builder()
                        .code("P002")
                        .name("Another Test Product")
                        .slug("another-test-product")
                        .description("Description for another test product")
                        .price(BigDecimal.valueOf(200.00))
                        .currency("EUR")
                        .status(ProductStatus.DRAFT)
                        .categoryId(1L)
                        .thumbnailUrl("http://example.com/another_thumb.jpg")
                        .build();
        productService.createProduct(productDTO);

        Product savedProduct = productRepository.findAll().getFirst();

        ProductDTO updateDTO =
                ProductDTO.builder()
                        .id(savedProduct.getId())
                        .code(savedProduct.getCode())
                        .name(savedProduct.getName())
                        .slug(savedProduct.getSlug())
                        .description(savedProduct.getDescription())
                        .price(savedProduct.getPrice())
                        .currency(savedProduct.getCurrency())
                        .categoryId(savedProduct.getCategoryId())
                        .status(ProductStatus.INACTIVE)
                        .thumbnailUrl(savedProduct.getThumbnailUrl())
                        .build();
        productService.updateProduct(savedProduct.getId(), updateDTO);

        // ProductUpdatedEvent is always published on any update, regardless of status
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10));

        assertFalse(records.isEmpty(), "ProductUpdatedEvent should be published on any update");
        var record = records.iterator().next();
        JsonNode envelope = objectMapper.readTree(record.value());
        assertEquals("product.updated", envelope.get("eventType").asText());
        assertEquals("product-service", envelope.get("source").asText());
        assertEquals(savedProduct.getCode(), envelope.get("data").get("code").asText());
    }
}
