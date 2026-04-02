package huynv.notificationservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.event.BaseEvent;
import huynv.notificationservice.domain.NotificationType;
import huynv.notificationservice.exception.InvalidEventPayloadException;
import huynv.notificationservice.service.channel.NotificationMessage;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Translates inbound platform events into normalized notification messages and dispatches them to channels.
 */
@Service
public class NotificationProcessingService {

    private final ObjectMapper objectMapper;
    private final NotificationDispatchService dispatchService;

    /**
     * Creates a processor that parses BaseEvent envelopes and dispatches notifications.
     *
     * @param objectMapper ObjectMapper used to parse inbound JSON payloads.
     * @param dispatchService Dispatcher used to send notifications and persist delivery history.
     * @return Initializes a notification processing service.
     */
    public NotificationProcessingService(ObjectMapper objectMapper, NotificationDispatchService dispatchService) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.dispatchService = Objects.requireNonNull(dispatchService, "dispatchService");
    }

    /**
     * Processes an inbound BaseEvent payload by mapping it to a notification message and dispatching it.
     *
     * @param rawJson Raw JSON string representing a BaseEvent envelope.
     * @return Performs side effects by dispatching notifications and persisting delivery history.
     */
    public void processRawEvent(String rawJson) {
        BaseEvent<JsonNode> event = parseEnvelope(rawJson);
        NotificationIntent intent = toIntent(event, rawJson);
        if (intent == null) {
            return;
        }
        NotificationMessage message = new NotificationMessage(
                intent.notificationType(),
                intent.tenantId(),
                intent.userId(),
                intent.subject(),
                intent.templateName(),
                intent.templateModel(),
                intent.rawEventPayload()
        );
        dispatchService.dispatch(message);
    }

    /**
     * Parses a BaseEvent envelope from a JSON string payload.
     *
     * @param payload JSON payload containing the BaseEvent envelope.
     * @return Returns a parsed BaseEvent with JsonNode data for flexible field extraction.
     */
    public BaseEvent<JsonNode> parseEnvelope(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || root.isNull()) {
                throw new InvalidEventPayloadException("BaseEvent payload must not be null.");
            }

            String eventId = textOrNull(root, "eventId");
            String eventType = textOrNull(root, "eventType");
            String source = textOrNull(root, "source");
            Instant eventTime = instantOrNull(root, "eventTime");
            String aggregateId = textOrNull(root, "aggregateId");
            long aggregateVersion = longOrDefault(root, "aggregateVersion", 0L);
            String dataSchema = textOrNull(root, "dataSchema");
            String traceId = textOrNull(root, "traceId");
            String correlationId = textOrNull(root, "correlationId");
            String causationId = textOrNull(root, "causationId");
            JsonNode data = root.get("data");

            return new BaseEvent<>(
                    eventId,
                    eventType,
                    source,
                    eventTime,
                    aggregateId,
                    aggregateVersion,
                    dataSchema,
                    traceId,
                    correlationId,
                    causationId,
                    data
            );
        } catch (Exception ex) {
            throw new InvalidEventPayloadException("Failed to parse BaseEvent payload.", ex);
        }
    }

    /**
     * Validates required BaseEvent schema fields before processing to prevent poison-message retry loops.
     *
     * @param event Parsed BaseEvent envelope to validate.
     * @return Performs side effects by throwing when required fields are missing or invalid.
     */
    public void validateRequiredFields(BaseEvent<JsonNode> event) {
        Objects.requireNonNull(event, "event");
        if (event.eventId() == null || event.eventId().isBlank()) {
            throw new InvalidEventPayloadException("eventId must be present.");
        }
        if (event.eventType() == null || event.eventType().isBlank()) {
            throw new InvalidEventPayloadException("eventType must be present.");
        }
        if (event.eventTime() == null) {
            throw new InvalidEventPayloadException("eventTime must be present.");
        }
        String tenantIdText = tenantIdFromPayload(event.data());
        if (tenantIdText == null || tenantIdText.isBlank()) {
            throw new InvalidEventPayloadException("tenantId must be present in data.");
        }
        try {
            Long.parseLong(tenantIdText.trim());
        } catch (Exception ex) {
            throw new InvalidEventPayloadException("tenantId must be a valid long.", ex);
        }
    }

    /**
     * Maps a parsed BaseEvent envelope into a normalized notification intent for downstream processing.
     *
     * @param event Parsed BaseEvent envelope.
     * @param rawJson Raw JSON value used for auditing and persistent history.
     * @return Returns a normalized NotificationIntent or null when the event is not relevant for notifications.
     */
    public NotificationIntent toIntent(BaseEvent<JsonNode> event, String rawJson) {
        Objects.requireNonNull(event, "event");
        String eventType = event.eventType();
        if (eventType == null || eventType.isBlank()) {
            throw new InvalidEventPayloadException("eventType must be present.");
        }

        JsonNode data = event.data();
        Long tenantId = longOrNull(data, "tenantId");
        if (tenantId == null) {
            throw new InvalidEventPayloadException("tenantId must be present in event data.");
        }

        if ("order.created".equals(eventType)) {
            Long userId = longOrNull(data, "userId");
            UUID orderId = uuidOrNull(data, "orderId");
            Map<String, Object> model = new HashMap<>();
            model.put("orderId", orderId == null ? null : orderId.toString());
            model.put("tenantId", tenantId);
            model.put("userId", userId);
            model.put("status", textOrNull(data, "status"));
            model.put("totalAmount", textOrNull(data, "totalAmount"));
            model.put("currency", textOrNull(data, "currency"));
            model.put("timestamp", textOrNull(data, "timestamp"));
            return new NotificationIntent(
                    event.eventId(),
                    event.eventType(),
                    tenantId,
                    userId,
                    orderId,
                    NotificationType.ORDER_CREATED,
                    "Order confirmation",
                    "order-confirmation",
                    Map.copyOf(model),
                    rawJson,
                    event.traceId(),
                    event.correlationId()
            );
        }

        if ("order.cancelled".equals(eventType)) {
            Long userId = longOrNull(data, "userId");
            UUID orderId = uuidOrNull(data, "orderId");
            Map<String, Object> model = new HashMap<>();
            model.put("orderId", orderId == null ? null : orderId.toString());
            model.put("tenantId", tenantId);
            model.put("userId", userId);
            model.put("status", textOrNull(data, "status"));
            model.put("timestamp", textOrNull(data, "timestamp"));
            return new NotificationIntent(
                    event.eventId(),
                    event.eventType(),
                    tenantId,
                    userId,
                    orderId,
                    NotificationType.ORDER_CANCELLED,
                    "Order cancelled",
                    "order-cancelled",
                    Map.copyOf(model),
                    rawJson,
                    event.traceId(),
                    event.correlationId()
            );
        }

        if ("payment.completed".equals(eventType) || "payment.succeeded".equals(eventType)) {
            UUID orderId = uuidOrNull(data, "orderId");
            Map<String, Object> model = new HashMap<>();
            model.put("orderId", orderId == null ? null : orderId.toString());
            model.put("tenantId", tenantId);
            model.put("paymentId", textOrNull(data, "paymentId"));
            model.put("transactionId", textOrNull(data, "transactionId"));
            return new NotificationIntent(
                    event.eventId(),
                    event.eventType(),
                    tenantId,
                    null,
                    orderId,
                    NotificationType.PAYMENT_SUCCEEDED,
                    "Payment succeeded",
                    "payment-succeeded",
                    Map.copyOf(model),
                    rawJson,
                    event.traceId(),
                    event.correlationId()
            );
        }

        if ("payment.failed".equals(eventType)) {
            UUID orderId = uuidOrNull(data, "orderId");
            Map<String, Object> model = new HashMap<>();
            model.put("orderId", orderId == null ? null : orderId.toString());
            model.put("tenantId", tenantId);
            model.put("paymentId", textOrNull(data, "paymentId"));
            model.put("reason", textOrNull(data, "reason"));
            return new NotificationIntent(
                    event.eventId(),
                    event.eventType(),
                    tenantId,
                    null,
                    orderId,
                    NotificationType.PAYMENT_FAILED,
                    "Payment failed",
                    "payment-failed",
                    Map.copyOf(model),
                    rawJson,
                    event.traceId(),
                    event.correlationId()
            );
        }

        return null;
    }

    /**
     * Extracts tenantId from the event data payload to enforce tenant isolation without trusting headers.
     *
     * @param data JsonNode payload from the BaseEvent data field.
     * @return Returns the tenantId value as text or null when missing.
     */
    private static String tenantIdFromPayload(JsonNode data) {
        if (data == null) {
            return null;
        }
        JsonNode value = data.get("tenantId");
        if (value == null || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    /**
     * Extracts a long value from a JSON node field when possible.
     *
     * @param node JSON node containing fields.
     * @param field Field name to extract.
     * @return Returns the extracted long value or null when missing or invalid.
     */
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

    /**
     * Extracts a string value from a JSON node field when possible.
     *
     * @param node JSON node containing fields.
     * @param field Field name to extract.
     * @return Returns the extracted string value or null when missing.
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

    /**
     * Parses an ISO-8601 instant from a JSON node field when possible.
     *
     * @param node JSON node containing fields.
     * @param field Field name to extract.
     * @return Returns a parsed Instant or null when missing or invalid.
     */
    private static Instant instantOrNull(JsonNode node, String field) {
        String value = textOrNull(node, field);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Extracts a long value from a JSON node field, falling back to a default on missing or invalid values.
     *
     * @param node JSON node containing fields.
     * @param field Field name to extract.
     * @param defaultValue Default value returned when the field is missing or invalid.
     * @return Returns the parsed long value or the provided default.
     */
    private static long longOrDefault(JsonNode node, String field, long defaultValue) {
        Long value = longOrNull(node, field);
        return value == null ? defaultValue : value;
    }

    /**
     * Extracts a UUID value from a JSON node field when possible.
     *
     * @param node JSON node containing fields.
     * @param field Field name to extract.
     * @return Returns the extracted UUID or null when missing or invalid.
     */
    private static UUID uuidOrNull(JsonNode node, String field) {
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
}

