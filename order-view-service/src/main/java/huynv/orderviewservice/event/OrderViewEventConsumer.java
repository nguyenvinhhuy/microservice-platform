package huynv.orderviewservice.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.event.idempotency.IdempotencyService;
import huynv.event.BaseEvent;
import huynv.orderviewservice.service.OrderViewProjectionService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
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
        try {
            BaseEvent<JsonNode> event = parseEnvelope(record.value());
            putMdc(record, event);
            if (idempotencyService.alreadyProcessed(event.eventId())) {
                return;
            }
            applyOrderEvent(event);
            idempotencyService.markProcessed(event.eventId());
        } finally {
            clearMdc();
        }
    }

    /**
     * Applies payment events to the order_view projection.
     *
     * @param record Kafka record containing the payment event envelope.
     * @return Performs side effects by updating the order view projection state.
     */
    @KafkaListener(topics = "${orderview.kafka.payment-topic:payment.events}", groupId = "${orderview.kafka.group-id:order-view-service}")
    public void onPaymentEvent(ConsumerRecord<String, String> record) {
        try {
            BaseEvent<JsonNode> event = parseEnvelope(record.value());
            putMdc(record, event);
            if (idempotencyService.alreadyProcessed(event.eventId())) {
                return;
            }
            applyPaymentEvent(event);
            idempotencyService.markProcessed(event.eventId());
        } finally {
            clearMdc();
        }
    }

    /**
     * Applies inventory events to the order_view projection.
     *
     * @param record Kafka record containing the inventory event envelope.
     * @return Performs side effects by updating the order view projection state.
     */
    @KafkaListener(topics = "${orderview.kafka.inventory-topic:inventory.events}", groupId = "${orderview.kafka.group-id:order-view-service}")
    public void onInventoryEvent(ConsumerRecord<String, String> record) {
        try {
            BaseEvent<JsonNode> event = parseEnvelope(record.value());
            putMdc(record, event);
            if (idempotencyService.alreadyProcessed(event.eventId())) {
                return;
            }
            applyInventoryEvent(event);
            idempotencyService.markProcessed(event.eventId());
        } finally {
            clearMdc();
        }
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

    /**
     * Populates MDC values from Kafka metadata and the event envelope for projection-consumer logs.
     *
     * @param record Kafka record currently being processed.
     * @param event Parsed event envelope carrying correlation fields.
     * @return Performs side effects by setting MDC values for the current thread.
     */
    private static void putMdc(ConsumerRecord<String, String> record, BaseEvent<JsonNode> event) {
        putIfPresent("eventId", event == null ? null : event.eventId());
        putIfPresent("correlationId", event == null ? null : event.correlationId());
        putIfPresent("causationId", event == null ? null : event.causationId());
        putIfPresent("aggregateId", event == null ? null : event.aggregateId());
        putIfPresent("tenantId", event == null ? null : stringify(longOrNull(event.data(), "tenantId")));
        putIfPresent("userId", event == null ? null : stringify(longOrNull(event.data(), "userId")));
        putIfPresent("orderId", event == null ? null : textOrNull(event.data(), "orderId"));
        putIfPresent("productId", event == null ? null : textOrNull(event.data(), "productId"));
        putIfPresent("paymentId", event == null ? null : textOrNull(event.data(), "paymentId"));
        putIfPresent("topic", record == null ? null : record.topic());
        putIfPresent("partition", record == null ? null : String.valueOf(record.partition()));
        putIfPresent("offset", record == null ? null : String.valueOf(record.offset()));
    }

    /**
     * Clears MDC fields used during projection updates to avoid leaking context between Kafka records.
     *
     * @return Performs side effects by removing MDC values for the current thread.
     */
    private static void clearMdc() {
        MDC.remove("eventId");
        MDC.remove("correlationId");
        MDC.remove("causationId");
        MDC.remove("aggregateId");
        MDC.remove("tenantId");
        MDC.remove("userId");
        MDC.remove("orderId");
        MDC.remove("productId");
        MDC.remove("paymentId");
        MDC.remove("topic");
        MDC.remove("partition");
        MDC.remove("offset");
    }

    /**
     * Converts a nullable numeric identifier into a string for MDC storage.
     *
     * @param value Numeric identifier that may be null.
     * @return Returns the string representation of the value, or null when absent.
     */
    private static String stringify(Long value) {
        return value == null ? null : value.toString();
    }

    /**
     * Writes one MDC entry only when the value is non-null and non-blank.
     *
     * @param key MDC key name.
     * @param value MDC value to set.
     * @return Performs a side effect by updating MDC for the current thread.
     */
    private static void putIfPresent(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        MDC.put(key, value);
    }
}


