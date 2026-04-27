package huynv.auditlogservice.event;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.auditlogservice.service.AuditLogService;
import huynv.event.BaseEvent;
import huynv.event.idempotency.IdempotencyService;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Consumes order, payment, inventory, and product events from Kafka and persists immutable audit log entries.
 */
@Component
@ConditionalOnProperty(prefix = "auditlog.kafka", name = "consumer-enabled", havingValue = "true", matchIfMissing = true)
public class AuditEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(AuditEventConsumer.class);

    private final ObjectMapper objectMapper;
    private final IdempotencyService idempotencyService;
    private final AuditLogService auditLogService;

    /**
     * Creates an audit event consumer wired to the idempotency and audit log services.
     *
     * @param objectMapper ObjectMapper used to parse inbound Kafka message payloads.
     * @param idempotencyService Idempotency service used to skip duplicate deliveries.
     * @param auditLogService Audit log service used to persist the immutable audit entry.
     * @return Initializes an audit event consumer instance.
     */
    public AuditEventConsumer(ObjectMapper objectMapper,
                               IdempotencyService idempotencyService,
                               AuditLogService auditLogService) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.idempotencyService = Objects.requireNonNull(idempotencyService, "idempotencyService");
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService");
    }

    /**
     * Handles order events received from the order events Kafka topic.
     *
     * @param record Kafka record containing the order event envelope payload.
     * @param acknowledgment Manual acknowledgment handle for committing the Kafka offset on success.
     * @return Performs a side effect by persisting an audit log entry for the order event.
     */
    @KafkaListener(
            id = "audit-order-consumer",
            topics = "${auditlog.kafka.order-topic:order.events}",
            groupId = "${auditlog.kafka.group-id:audit-log-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderEvent(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        handle(record, acknowledgment);
    }

    /**
     * Handles payment events received from the payment events Kafka topic.
     *
     * @param record Kafka record containing the payment event envelope payload.
     * @param acknowledgment Manual acknowledgment handle for committing the Kafka offset on success.
     * @return Performs a side effect by persisting an audit log entry for the payment event.
     */
    @KafkaListener(
            id = "audit-payment-consumer",
            topics = "${auditlog.kafka.payment-topic:payment.events}",
            groupId = "${auditlog.kafka.group-id:audit-log-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentEvent(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        handle(record, acknowledgment);
    }

    /**
     * Handles inventory events received from the inventory events Kafka topic.
     *
     * @param record Kafka record containing the inventory event envelope payload.
     * @param acknowledgment Manual acknowledgment handle for committing the Kafka offset on success.
     * @return Performs a side effect by persisting an audit log entry for the inventory event.
     */
    @KafkaListener(
            id = "audit-inventory-consumer",
            topics = "${auditlog.kafka.inventory-topic:inventory.events}",
            groupId = "${auditlog.kafka.group-id:audit-log-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInventoryEvent(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        handle(record, acknowledgment);
    }

    /**
     * Handles product events received from the product events Kafka topic.
     *
     * @param record Kafka record containing the product event envelope payload.
     * @param acknowledgment Manual acknowledgment handle for committing the Kafka offset on success.
     * @return Performs a side effect by persisting an audit log entry for the product event.
     */
    @KafkaListener(
            id = "audit-product-consumer",
            topics = "${auditlog.kafka.product-topic:product.events}",
            groupId = "${auditlog.kafka.group-id:audit-log-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onProductEvent(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        handle(record, acknowledgment);
    }

    /**
     * Shared handler that parses the event envelope, checks idempotency, persists the audit log, and acknowledges the offset.
     *
     * @param record Kafka record containing the raw event payload string.
     * @param acknowledgment Manual acknowledgment handle for committing the Kafka offset on success.
     * @return Performs side effects by persisting the audit entry and committing the Kafka offset.
     */
    private void handle(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(acknowledgment, "acknowledgment");

        String rawPayload = record.value();
        if (rawPayload == null || rawPayload.isBlank()) {
            log.warn("Received empty kafka message topic={} partition={} offset={}",
                    record.topic(), record.partition(), record.offset());
            acknowledgment.acknowledge();
            return;
        }

        try {
            BaseEvent<JsonNode> envelope = parseEnvelope(rawPayload);
            putMdc(record, envelope);

            String eventId = envelope.eventId();
            if (eventId == null || eventId.isBlank()) {
                log.warn("Received event without eventId topic={} offset={}", record.topic(), record.offset());
                acknowledgment.acknowledge();
                return;
            }

            if (idempotencyService.alreadyProcessed(eventId)) {
                log.debug("Skipping duplicate event eventId={} topic={}", eventId, record.topic());
                acknowledgment.acknowledge();
                return;
            }

            Long tenantId = extractLong(envelope.data(), "tenantId");
            Long userId = extractLong(envelope.data(), "userId");

            auditLogService.record(
                    eventId,
                    envelope.eventType() != null ? envelope.eventType() : record.topic(),
                    envelope.source(),
                    tenantId,
                    userId,
                    envelope.aggregateId(),
                    envelope.correlationId(),
                    envelope.causationId(),
                    rawPayload
            );

            idempotencyService.markProcessed(eventId);
            acknowledgment.acknowledge();

        } catch (Exception ex) {
            log.error("Failed to process audit event topic={} partition={} offset={} errorClass={} message={}",
                    record.topic(), record.partition(), record.offset(),
                    ex.getClass().getName(), ex.getMessage(), ex);
            throw new IllegalStateException("Audit event processing failed.", ex);
        } finally {
            clearMdc();
        }
    }

    /**
     * Parses a raw JSON string into a typed BaseEvent envelope wrapping a JsonNode data payload.
     *
     * @param payload Raw JSON string representing the Kafka message value.
     * @return Returns the deserialized BaseEvent envelope.
     */
    private BaseEvent<JsonNode> parseEnvelope(String payload) {
        try {
            TypeReference<BaseEvent<JsonNode>> type = new TypeReference<>() {};
            return objectMapper.readValue(payload, type);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to parse BaseEvent envelope.", ex);
        }
    }

    /**
     * Extracts a Long value from a JsonNode field, returning null when the field is absent or unparseable.
     *
     * @param node JsonNode data payload from the event envelope.
     * @param field Field name to read from the node.
     * @return Returns the Long value for the named field, or null when not present or unparseable.
     */
    private static Long extractLong(JsonNode node, String field) {
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
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /**
     * Populates MDC values from Kafka record metadata and the parsed event envelope for structured logging.
     *
     * @param record Kafka record currently being processed.
     * @param envelope Parsed event envelope used to extract business correlation fields.
     * @return Performs side effects by setting MDC values for the current thread.
     */
    private static void putMdc(ConsumerRecord<String, String> record, BaseEvent<JsonNode> envelope) {
        putIfPresent("eventId", envelope == null ? null : envelope.eventId());
        putIfPresent("correlationId", envelope == null ? null : envelope.correlationId());
        putIfPresent("causationId", envelope == null ? null : envelope.causationId());
        putIfPresent("aggregateId", envelope == null ? null : envelope.aggregateId());
        putIfPresent("tenantId", envelope == null ? null : stringify(extractLong(envelope.data(), "tenantId")));
        putIfPresent("userId", envelope == null ? null : stringify(extractLong(envelope.data(), "userId")));
        putIfPresent("orderId", envelope == null ? null : textOrNull(envelope.data(), "orderId"));
        putIfPresent("productId", envelope == null ? null : textOrNull(envelope.data(), "productId"));
        putIfPresent("paymentId", envelope == null ? null : textOrNull(envelope.data(), "paymentId"));
        putIfPresent("topic", record == null ? null : record.topic());
        putIfPresent("partition", record == null ? null : String.valueOf(record.partition()));
        putIfPresent("offset", record == null ? null : String.valueOf(record.offset()));
    }

    /**
     * Clears MDC keys used during Kafka audit processing to avoid leaking context across reused threads.
     *
     * @return Performs side effects by removing MDC keys for the current thread.
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
     * Writes one MDC entry only when the value is non-null and non-blank.
     *
     * @param key MDC key name.
     * @param value MDC value to set for the current thread.
     * @return Performs a side effect by updating MDC when the value is present.
     */
    private static void putIfPresent(String key, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        MDC.put(key, value);
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
     * Extracts a textual field from the event payload for MDC correlation fields.
     *
     * @param node JsonNode payload to read from.
     * @param field Field name to extract.
     * @return Returns the textual field value, or null when absent.
     */
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

