package huynv.orderviewservice.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.event.idempotency.IdempotencyService;
import huynv.event.BaseEvent;
import huynv.orderviewservice.service.OrderViewProjectionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Consumes order, payment, and inventory events to update the order_view read model.
 */
@Component
public class OrderViewEventConsumer {

    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;
    private final OrderViewProjectionService projectionService;

    /**
     * Creates an order view consumer that parses BaseEvent envelopes and updates the read model.
     *
     * @param objectMapper ObjectMapper used to parse inbound JSON payloads.
     * @param idempotencyService Idempotency service used to skip duplicate deliveries.
     * @param projectionService Projection service used to persist denormalized view state.
     * @return Initializes an order view consumer instance.
     */
    public OrderViewEventConsumer(ObjectMapper objectMapper, IdempotencyService idempotencyService, OrderViewProjectionService projectionService) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.idempotencyService = Objects.requireNonNull(idempotencyService, "idempotencyService");
        this.projectionService = Objects.requireNonNull(projectionService, "projectionService");
    }

    /**
     * Applies order events to the order_view projection.
     *
     * @param record Kafka record containing the order event envelope.
     * @return Performs side effects by updating the order view projection state.
     */
    @KafkaListener(topics = "${orderview.kafka.order-topic:order.events}", groupId = "${orderview.kafka.group-id:order-view-service}")
    public void onOrderEvent(ConsumerRecord<String, String> record) {
        BaseEvent<JsonNode> event = parseEnvelope(record.value());
        if (idempotencyService.alreadyProcessed(event.eventId())) {
            return;
        }
        applyOrderEvent(event);
        idempotencyService.markProcessed(event.eventId());
    }

    /**
     * Applies payment events to the order_view projection.
     *
     * @param record Kafka record containing the payment event envelope.
     * @return Performs side effects by updating the order view projection state.
     */
    @KafkaListener(topics = "${orderview.kafka.payment-topic:payment.events}", groupId = "${orderview.kafka.group-id:order-view-service}")
    public void onPaymentEvent(ConsumerRecord<String, String> record) {
        BaseEvent<JsonNode> event = parseEnvelope(record.value());
        if (idempotencyService.alreadyProcessed(event.eventId())) {
            return;
        }
        applyPaymentEvent(event);
        idempotencyService.markProcessed(event.eventId());
    }

    /**
     * Applies inventory events to the order_view projection.
     *
     * @param record Kafka record containing the inventory event envelope.
     * @return Performs side effects by updating the order view projection state.
     */
    @KafkaListener(topics = "${orderview.kafka.inventory-topic:inventory.events}", groupId = "${orderview.kafka.group-id:order-view-service}")
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

    private void applyOrderEvent(BaseEvent<JsonNode> event) {
        if ("order.created".equals(event.eventType())) {
            JsonNode data = event.data();
            Long tenantId = longOrNull(data, "tenantId");
            UUID orderId = uuidOrNull(data, "orderId");
            Long userId = longOrNull(data, "userId");
            String status = textOrNull(data, "status");
            BigDecimal total = decimalOrNull(data, "totalAmount");
            OffsetDateTime createdAt = offsetDateTimeOrNow(textOrNull(data, "timestamp"));
            if (tenantId == null || orderId == null || userId == null) {
                return;
            }
            projectionService.upsertCreated(tenantId, orderId, userId, status, total, createdAt);
        }
        if ("order.paid".equals(event.eventType())) {
            JsonNode data = event.data();
            Long tenantId = longOrNull(data, "tenantId");
            UUID orderId = uuidOrNull(data, "orderId");
            if (tenantId == null || orderId == null) {
                return;
            }
            projectionService.updateOrderStatus(tenantId, orderId, "PAYMENT_COMPLETED");
        }
        if ("order.failed".equals(event.eventType())) {
            JsonNode data = event.data();
            Long tenantId = longOrNull(data, "tenantId");
            UUID orderId = uuidOrNull(data, "orderId");
            if (tenantId == null || orderId == null) {
                return;
            }
            projectionService.updateOrderStatus(tenantId, orderId, "ORDER_FAILED");
        }
    }

    private void applyPaymentEvent(BaseEvent<JsonNode> event) {
        if ("payment.completed".equals(event.eventType())) {
            JsonNode data = event.data();
            Long tenantId = longOrNull(data, "tenantId");
            UUID orderId = uuidOrNull(data, "orderId");
            if (tenantId == null || orderId == null) {
                return;
            }
            projectionService.updatePaymentStatus(tenantId, orderId, "PAYMENT_COMPLETED");
            return;
        }
        if ("payment.failed".equals(event.eventType())) {
            JsonNode data = event.data();
            Long tenantId = longOrNull(data, "tenantId");
            UUID orderId = uuidOrNull(data, "orderId");
            if (tenantId == null || orderId == null) {
                return;
            }
            projectionService.updatePaymentStatus(tenantId, orderId, "PAYMENT_FAILED");
        }
    }

    private void applyInventoryEvent(BaseEvent<JsonNode> event) {
        if ("inventory.stock.reserved".equals(event.eventType())) {
            JsonNode data = event.data();
            Long tenantId = longOrNull(data, "tenantId");
            UUID orderId = uuidOrNull(data, "orderId");
            if (tenantId == null || orderId == null) {
                return;
            }
            projectionService.updateStockStatus(tenantId, orderId, "STOCK_RESERVED");
            return;
        }
        if ("inventory.stock.confirmed".equals(event.eventType())) {
            JsonNode data = event.data();
            Long tenantId = longOrNull(data, "tenantId");
            UUID orderId = uuidOrNull(data, "orderId");
            if (tenantId == null || orderId == null) {
                return;
            }
            projectionService.updateStockStatus(tenantId, orderId, "STOCK_RESERVED");
            return;
        }
        if ("inventory.stock.released".equals(event.eventType())) {
            JsonNode data = event.data();
            Long tenantId = longOrNull(data, "tenantId");
            UUID orderId = uuidOrNull(data, "orderId");
            if (tenantId == null || orderId == null) {
                return;
            }
            projectionService.updateStockStatus(tenantId, orderId, "STOCK_RELEASED");
        }
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

    private static UUID uuidOrNull(JsonNode node, String field) {
        if (node == null || field == null) {
            return null;
        }
        String value = textOrNull(node, field);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (Exception ignored) {
            return null;
        }
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

    private static OffsetDateTime offsetDateTimeOrNow(String instant) {
        if (instant == null || instant.isBlank()) {
            return OffsetDateTime.now();
        }
        try {
            return java.time.Instant.parse(instant).atOffset(java.time.ZoneOffset.UTC);
        } catch (Exception ignored) {
            return OffsetDateTime.now();
        }
    }
}


