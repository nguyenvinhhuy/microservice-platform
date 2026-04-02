package huynv.notificationservice.event;

import com.fasterxml.jackson.databind.JsonNode;
import huynv.event.BaseEvent;
import huynv.eventinfra.config.NotificationProperties;
import huynv.eventinfra.exception.InvalidEventPayloadException;
import huynv.eventinfra.metrics.NotificationMetrics;
import huynv.eventinfra.outbox.KafkaOutboxPurpose;
import huynv.eventinfra.outbox.KafkaOutboxService;
import huynv.notificationservice.service.NotificationProcessingService;
import huynv.eventinfra.util.MdcUtil;
import huynv.eventinfra.util.TraceHeaderUtil;
import huynv.eventinfra.util.TracingUtil;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Consumes upstream events and republishes them into the internal notification events topic.
 */
@Component
@ConditionalOnProperty(prefix = "notification.kafka", name = "consumer-enabled", havingValue = "true", matchIfMissing = true)
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final NotificationProcessingService processingService;
    private final NotificationProperties properties;
    private final KafkaOutboxService outboxService;
    private final NotificationMetrics metrics;
    private final Tracer tracer;

    /**
     * Creates an event consumer that republishes events into the internal notification events topic.
     *
     * @param processingService Processing service used to parse inbound event envelopes.
     * @param properties Notification properties containing internal topic names.
     * @param outboxService Outbox service used to persist internal publishes for asynchronous Kafka publishing.
     * @param metrics Metrics used to track processing latency and outcomes.
     * @param tracer Tracer used to create spans for consumption and downstream processing.
     * @return Initializes a notification event consumer.
     */
     public NotificationEventConsumer(NotificationProcessingService processingService,
                                      NotificationProperties properties,
                                      KafkaOutboxService outboxService,
                                      NotificationMetrics metrics,
                                      Tracer tracer) {
        this.processingService = Objects.requireNonNull(processingService, "processingService");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.outboxService = Objects.requireNonNull(outboxService, "outboxService");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.tracer = Objects.requireNonNull(tracer, "tracer");
    }

    /**
     * Handles order events by republishing them into the internal notification events topic.
     *
     * @param record Kafka record containing an order event envelope.
     * @param acknowledgment Manual acknowledgment handle for committing offsets on success.
     * @return Performs side effects by publishing the event into the internal topic.
     */
    @KafkaListener(
            id = "notification-order-consumer",
            topics = "${notification.kafka.order-topic:order.events}",
            groupId = "${notification.kafka.group-id:notification-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onOrderEvent(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        handle(record, acknowledgment);
    }

    /**
     * Handles payment events by republishing them into the internal notification events topic.
     *
     * @param record Kafka record containing a payment event envelope.
     * @param acknowledgment Manual acknowledgment handle for committing offsets on success.
     * @return Performs side effects by publishing the event into the internal topic.
     */
    @KafkaListener(
            id = "notification-payment-consumer",
            topics = "${notification.kafka.payment-topic:payment.events}",
            groupId = "${notification.kafka.group-id:notification-service}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onPaymentEvent(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        handle(record, acknowledgment);
    }

    /**
     * Applies shared processing behavior including tracing, MDC setup, internal topic publish, and manual acknowledgment.
     *
     * @param record Kafka record containing the upstream event payload.
     * @param acknowledgment Manual acknowledgment handle for committing offsets on success.
     * @return Performs side effects by publishing the event into the internal notification events topic.
     */
    private void handle(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        long startNanos = System.nanoTime();
        Span span = null;
        Tracer.SpanInScope scope = null;
        try {
            Objects.requireNonNull(record, "record");
            Objects.requireNonNull(acknowledgment, "acknowledgment");

            BaseEvent<JsonNode> envelope = processingService.parseEnvelope(record.value());
            processingService.validateRequiredFields(envelope);
            span = TracingUtil.startSpan(tracer, "notification.consume", envelope == null ? null : envelope.traceId());
            scope = tracer.withSpan(span);

            putMdc(envelope, record);

            String tenantId = tenantIdFromPayload(envelope == null ? null : envelope.data());
            String key = routingKey(tenantId, envelope);

            Map<String, String> headers = new HashMap<>();
            record.headers().forEach(h -> headers.put(h.key(), h.value() == null ? null : new String(h.value(), StandardCharsets.UTF_8)));
            headers.put("original_topic", record.topic());
            headers.put("original_partition", String.valueOf(record.partition()));
            headers.put("original_offset", String.valueOf(record.offset()));
            TraceHeaderUtil.putTraceHeaders(headers,
                    envelope == null ? null : envelope.traceId(),
                    envelope == null ? null : envelope.correlationId(),
                    (envelope == null ? "" : envelope.eventId()) + "|" + record.topic() + "|" + record.partition() + "|" + record.offset(),
                    header(record, "tracestate"));
            headers.put("eventId", envelope == null ? null : envelope.eventId());
            headers.put("eventType", envelope == null ? null : envelope.eventType());
            headers.put("tenantId", tenantId);

            outboxService.enqueue(properties.getKafka().getEventsTopic(), key, record.value(), headers, KafkaOutboxPurpose.INTERNAL, OffsetDateTime.now());
            acknowledgment.acknowledge();

            log.info("Event republished eventId={} eventType={} aggregateId={} targetTopic={}",
                    envelope == null ? null : envelope.eventId(),
                    envelope == null ? null : envelope.eventType(),
                    envelope == null ? null : envelope.aggregateId(),
                    properties.getKafka().getEventsTopic());
        } catch (Exception ex) {
            if (span != null) {
                span.error(ex);
            }
            log.error("Event processing failed topic={} partition={} offset={} errorClass={} message={}",
                    record == null ? null : record.topic(),
                    record == null ? null : record.partition(),
                    record == null ? null : record.offset(),
                    ex.getClass().getName(),
                    ex.getMessage(),
                    ex);
            if (ex instanceof InvalidEventPayloadException invalidEventPayloadException) {
                throw invalidEventPayloadException;
            }
            throw new IllegalStateException("Upstream event processing failed.", ex);
        } finally {
            try {
                metrics.recordProcessingLatency(startNanos);
            } finally {
                MdcUtil.clear();
                if (scope != null) {
                    scope.close();
                }
                if (span != null) {
                    span.end();
                }
            }
        }
    }

    /**
     * Builds a routing key used for tenant-aware partition distribution.
     *
     * @param tenantId Tenant identifier extracted from the payload.
     * @param envelope Parsed event envelope.
     * @return Returns a routing key suitable for Kafka partitioning.
     */
    private static String routingKey(String tenantId, BaseEvent<JsonNode> envelope) {
        String safeTenant = tenantId == null || tenantId.isBlank() ? "unknown" : tenantId.trim();
        if (envelope == null) {
            return safeTenant + ":unknown";
        }
        String aggregateId = envelope.aggregateId();
        if (aggregateId != null && !aggregateId.isBlank()) {
            return safeTenant + ":" + aggregateId.trim();
        }
        String eventId = envelope.eventId();
        if (eventId != null && !eventId.isBlank()) {
            return safeTenant + ":" + eventId.trim();
        }
        return safeTenant + ":unknown";
    }

    /**
     * Populates MDC keys used by structured logging from the event payload and safe internal headers.
     *
     * @param envelope Parsed BaseEvent envelope used as the primary source of correlation identifiers.
     * @param record Kafka record containing internal headers used as a fallback for trace propagation.
     * @return Performs a side effect by updating MDC values for the current thread.
     */
    private static void putMdc(BaseEvent<JsonNode> envelope, ConsumerRecord<String, String> record) {
        Map<String, String> values = new HashMap<>();
        values.put("eventId", envelope.eventId());
        values.put("traceId", firstNonBlank(envelope.traceId(), header(record, "traceId")));
        values.put("correlationId", firstNonBlank(envelope.correlationId(), header(record, "correlationId")));
        values.put("tenantId", tenantIdFromPayload(envelope.data()));
        MdcUtil.putAll(values);
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
     * Reads a Kafka header value as UTF-8 text.
     *
     * @param record Kafka record containing headers.
     * @param key Header key to resolve.
     * @return Returns the header value as a string or null when missing.
     */
    private static String header(ConsumerRecord<String, String> record, String key) {
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
     * Returns the first non-blank value among two candidates.
     *
     * @param first First candidate value.
     * @param second Second candidate value.
     * @return Returns the first non-blank value or null when both are blank.
     */
    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}


