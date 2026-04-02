package huynv.productviewservice.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.event.idempotency.IdempotencyService;
import huynv.event.BaseEvent;
import huynv.productviewservice.service.ProductViewProjectionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * Consumes product and inventory events to update the product_view read model.
 */
@Component
public class ProductViewEventConsumer {

    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;
    private final ProductViewProjectionService projectionService;

    /**
     * Creates a product view consumer that parses BaseEvent envelopes and updates the read model.
     *
     * @param objectMapper ObjectMapper used to parse inbound JSON payloads.
     * @param idempotencyService Idempotency service used to skip duplicate deliveries.
     * @param projectionService Projection service used to persist denormalized view state.
     * @return Initializes a product view consumer instance.
     */
    public ProductViewEventConsumer(ObjectMapper objectMapper, IdempotencyService idempotencyService, ProductViewProjectionService projectionService) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.idempotencyService = Objects.requireNonNull(idempotencyService, "idempotencyService");
        this.projectionService = Objects.requireNonNull(projectionService, "projectionService");
    }

    /**
     * Applies product events to the product_view projection.
     *
     * @param record Kafka record containing the product event envelope.
     * @return Performs side effects by updating the product view projection state.
     */
    @KafkaListener(topics = "${productview.kafka.product-topic:product.events}", groupId = "${productview.kafka.group-id:product-view-service}")
    public void onProductEvent(ConsumerRecord<String, String> record) {
        BaseEvent<JsonNode> event = parseEnvelope(record.value());
        if (idempotencyService.alreadyProcessed(event.eventId())) {
            return;
        }
        applyProductEvent(event);
        idempotencyService.markProcessed(event.eventId());
    }

    /**
     * Applies inventory events to the product_view projection.
     *
     * @param record Kafka record containing the inventory event envelope.
     * @return Performs side effects by updating the product view projection state.
     */
    @KafkaListener(topics = "${productview.kafka.inventory-topic:inventory.events}", groupId = "${productview.kafka.group-id:product-view-service}")
    public void onInventoryEvent(ConsumerRecord<String, String> record) {
        BaseEvent<JsonNode> event = parseEnvelope(record.value());
        if (idempotencyService.alreadyProcessed(event.eventId())) {
            return;
        }
        applyInventoryEvent(event);
        idempotencyService.markProcessed(event.eventId());
    }

    private BaseEvent<JsonNode> parseEnvelope(String payload) {
        try {
            TypeReference<BaseEvent<JsonNode>> type = new TypeReference<>() {};
            return objectMapper.readValue(payload, type);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse BaseEvent payload.", ex);
        }
    }

    private void applyProductEvent(BaseEvent<JsonNode> event) {
        if ("product.updated".equals(event.eventType())) {
            JsonNode data = event.data();
            Long tenantId = longOrNull(data, "tenantId");
            Long productId = longOrNull(data, "productId");
            if (tenantId == null || productId == null) {
                return;
            }
            projectionService.upsertProduct(
                    tenantId,
                    productId,
                    textOrNull(data, "name"),
                    decimalOrNull(data, "price"),
                    "ACTIVE",
                    OffsetDateTime.now()
            );
        }
        if ("product.price.updated".equals(event.eventType())) {
            JsonNode data = event.data();
            Long tenantId = longOrNull(data, "tenantId");
            Long productId = longOrNull(data, "productId");
            if (tenantId == null || productId == null) {
                return;
            }
            projectionService.upsertProduct(
                    tenantId,
                    productId,
                    null,
                    decimalOrNull(data, "price"),
                    null,
                    OffsetDateTime.now()
            );
        }
    }

    private void applyInventoryEvent(BaseEvent<JsonNode> event) {
        if (!"inventory.stock.updated".equals(event.eventType())) {
            return;
        }
        JsonNode data = event.data();
        Long tenantId = longOrNull(data, "tenantId");
        Long productId = longOrNull(data, "productId");
        Integer availableStock = intOrNull(data, "availableStock");
        if (tenantId == null || productId == null) {
            return;
        }
        projectionService.updateStock(tenantId, productId, availableStock, OffsetDateTime.now());
    }

    private static Long longOrNull(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.longValue();
        }
        if (value.isTextual()) {
            try {
                return Long.parseLong(value.asText());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.intValue();
        }
        if (value.isTextual()) {
            try {
                return Integer.parseInt(value.asText());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static BigDecimal decimalOrNull(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        if (value.isTextual()) {
            try {
                return new BigDecimal(value.asText());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    
}


