package huynv.productservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.event.BaseEvent;
import huynv.event.product.ProductUpdatedEvent;
import huynv.productservice.config.KafkaTopicConfig;
import huynv.productservice.dto.ProductDTO;
import huynv.productservice.model.Product;
import huynv.productservice.model.ProductStatus;
import huynv.productservice.repository.ProductRepository;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class ProductServiceKafkaE2ETest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void kafkaProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        // Ensure that the test uses an in-memory database or a test-specific database
        registry.add("spring.datasource.url", () -> "jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL");
        registry.add("spring.datasource.driver-class-name", () -> "org.h2.Driver");
        registry.add("spring.datasource.username", () -> "sa");
        registry.add("spring.datasource.password", () -> "");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ObjectMapper objectMapper; // To deserialize Kafka messages manually if needed

    private Consumer<String, String> consumer;

    @BeforeEach
    void setUp() {
        // Clear repository before each test
        productRepository.deleteAll();

        // Setup Kafka consumer for the test
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-group-" + System.currentTimeMillis()); // Unique group ID for each test
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        ConsumerFactory<String, String> cf = new DefaultKafkaConsumerFactory<>(props);
        consumer = cf.createConsumer();
        consumer.subscribe(Collections.singletonList(KafkaTopicConfig.PRODUCT_EVENTS_TOPIC));
        consumer.poll(Duration.ofMillis(100)); // Poll once to ensure subscription is active
    }

    @Test
    @Transactional // Ensure the DB transaction is committed before Kafka event is processed
    void shouldPublishProductActivatedEventWhenStatusChangesToActive() throws Exception {
        // 1. Create a product in DRAFT status
        ProductDTO productDTO = ProductDTO.builder()
                .code("P001")
                .name("Test Product")
                .slug("test-product")
                .description("Description for test product")
                .price(BigDecimal.valueOf(100.00))
                .currency("USD")
                .status(ProductStatus.DRAFT)
                .thumbnailUrl("http://example.com/thumb.jpg")
                .build();
        productService.createProduct(productDTO);

        // Retrieve the saved product to get its ID.
        Product savedProduct = productRepository.findAll().getFirst();

        // 2. Update the product status to ACTIVE
        ProductDTO updateDTO = ProductDTO.builder()
                .id(savedProduct.getId())
                .code(savedProduct.getCode())
                .name(savedProduct.getName())
                .slug(savedProduct.getSlug())
                .description(savedProduct.getDescription())
                .price(savedProduct.getPrice())
                .currency(savedProduct.getCurrency())
                .status(ProductStatus.ACTIVE) // Change to ACTIVE
                .thumbnailUrl(savedProduct.getThumbnailUrl())
                .build();
        productService.updateProduct(savedProduct.getId(), updateDTO);

        // 3. Poll Kafka for the event
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(10)); // Wait for up to 10 seconds

        // 4. Assert the event was received and its content is correct
        assertFalse(records.isEmpty(), "Kafka record should not be empty");
        assertEquals(1, records.count(), "Should receive exactly one record");

        String payload = records.iterator().next().value();
        BaseEvent<ProductUpdatedEvent> receivedEvent = objectMapper.readValue(
                payload,
                new com.fasterxml.jackson.core.type.TypeReference<>() {
                }
        );
        assertNotNull(receivedEvent);
        assertNotNull(receivedEvent.eventId());
        assertEquals("product.updated", receivedEvent.eventType());
        assertEquals("product-" + savedProduct.getId(), receivedEvent.aggregateId());
        assertEquals(0L, receivedEvent.aggregateVersion());
        assertNotNull(receivedEvent.eventTime());
        assertEquals("product-service", receivedEvent.source());
        assertEquals("product.updated.v1", receivedEvent.dataSchema());
        assertNotNull(receivedEvent.correlationId());
        assertNotNull(receivedEvent.data());
        assertEquals(savedProduct.getId(), receivedEvent.data().productId());
        assertEquals(savedProduct.getCode(), receivedEvent.data().code());
        assertEquals(savedProduct.getName(), receivedEvent.data().name());
        assertEquals(savedProduct.getPrice().stripTrailingZeros(), receivedEvent.data().price().stripTrailingZeros());
        assertEquals(savedProduct.getCurrency(), receivedEvent.data().currency());

        // Verify the key
        assertEquals(String.valueOf(savedProduct.getId()), records.iterator().next().key());
    }

    @Test
    @Transactional
    void shouldNotPublishProductActivatedEventWhenStatusDoesNotChangeToActive() throws Exception {
        // 1. Create a product in DRAFT status
        ProductDTO productDTO = ProductDTO.builder()
                .code("P002")
                .name("Another Test Product")
                .slug("another-test-product")
                .description("Description for another test product")
                .price(BigDecimal.valueOf(200.00))
                .currency("EUR")
                .status(ProductStatus.DRAFT)
                .thumbnailUrl("http://example.com/another_thumb.jpg")
                .build();
        productService.createProduct(productDTO);

        Product savedProduct = productRepository.findAll().getFirst();

        // 2. Update the product status to INACTIVE (not ACTIVE)
        ProductDTO updateDTO = ProductDTO.builder()
                .id(savedProduct.getId())
                .code(savedProduct.getCode())
                .name(savedProduct.getName())
                .slug(savedProduct.getSlug())
                .description(savedProduct.getDescription())
                .price(savedProduct.getPrice())
                .currency(savedProduct.getCurrency())
                .status(ProductStatus.INACTIVE) // Change to INACTIVE
                .thumbnailUrl(savedProduct.getThumbnailUrl())
                .build();
        productService.updateProduct(savedProduct.getId(), updateDTO);

        // 3. Poll Kafka for the event
        ConsumerRecords<String, String> records = consumer.poll(Duration.ofSeconds(5)); // Wait for a short duration

        // 4. Assert no event was received
        assertTrue(records.isEmpty(), "No Kafka record should be received");
    }
}

