package huynv.notificationservice.event;

import com.fasterxml.jackson.databind.JsonNode;
import huynv.event.idempotency.IdempotencyService;
import huynv.event.BaseEvent;
import huynv.notificationservice.service.NotificationIngestionService;
import huynv.notificationservice.exception.InvalidEventPayloadException;
import huynv.eventinfra.metrics.NotificationMetrics;
import huynv.notificationservice.service.NotificationProcessingService;
import huynv.eventinfra.util.MdcUtil;
import huynv.eventinfra.util.TracingUtil;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Consumes internal notification events and expands them into per-channel notification jobs.
 */
@Component
@ConditionalOnExpression("${notification.kafka.consumer-enabled:true} && ${notification.dispatch.enabled:true}")
public class NotificationInternalEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationInternalEventConsumer.class);

    private final NotificationProcessingService processingService;
    private final NotificationIngestionService ingestionService;
    private final IdempotencyService idempotencyService;
    private final NotificationMetrics metrics;
    private final Tracer tracer;

    /**
     * Creates an internal consumer that enforces idempotency and expands events into downstream jobs.
     *
     * @param processingService Processing service used to parse inbound event envelopes.
     * @param ingestionService Ingestion service used to resolve recipients and enqueue per-channel jobs.
     * @param idempotencyService Idempotency service used to skip duplicate events.
     * @param metrics Metrics used to track processing latency and outcomes.
     * @param tracer Tracer used to create spans for internal processing.
     * @return Initializes a notification internal event consumer.
     */
    public NotificationInternalEventConsumer(NotificationProcessingService processingService,
                                             NotificationIngestionService ingestionService,
                                             IdempotencyService idempotencyService,
                                             NotificationMetrics metrics,
                                             Tracer tracer) {
        this.processingService = Objects.requireNonNull(processingService, "processingService");
        this.ingestionService = Objects.requireNonNull(ingestionService, "ingestionService");
        this.idempotencyService = Objects.requireNonNull(idempotencyService, "idempotencyService");
        this.metrics = Objects.requireNonNull(metrics, "metrics");
        this.tracer = Objects.requireNonNull(tracer, "tracer");
    }

    /**
     * Consumes internal notification events with manual acknowledgment.
     *
     * @param record Kafka record containing the internal BaseEvent payload.
     * @param acknowledgment Manual acknowledgment handle for committing offsets on success.
     * @return Performs side effects by publishing notification jobs and persisting processed markers.
     */
    @KafkaListener(
            id = "notification-internal-events-consumer",
            topics = "${notification.kafka.events-topic:notification.events}",
            groupId = "${notification.kafka.group-id:notification-service}-internal-events",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onInternalEvent(ConsumerRecord<String, String> record, Acknowledgment acknowledgment) {
        handle(record, acknowledgment);
    }

    /**
     * Applies shared processing behavior including tracing, MDC setup, idempotency checks, and manual acknowledgment.
     *
     * @param record Kafka record containing the event payload.
     * @param acknowledgment Manual acknowledgment handle for committing offsets on success.
     * @return Performs side effects by dispatching notifications and updating processed markers.
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
            span = TracingUtil.startSpan(tracer, "notification.internal.consume", envelope == null ? null : envelope.traceId());
            scope = tracer.withSpan(span);

            putMdc(envelope);
            // Platform contract: eventId is globally unique and stable across retries, so it is safe to deduplicate by eventId alone.
            String idempotencyKey = envelope.eventId();

            if (idempotencyService.alreadyProcessed(idempotencyKey)) {
                log.info("Duplicate event skipped eventId={} eventType={}", envelope.eventId(), envelope.eventType());
                acknowledgment.acknowledge();
                return;
            }

            ingestionService.ingest(record.value());
            idempotencyService.markProcessed(idempotencyKey);
            acknowledgment.acknowledge();

            log.info("Internal event processed eventId={} eventType={} aggregateId={}", envelope.eventId(), envelope.eventType(), envelope.aggregateId());
        } catch (Exception ex) {
            if (span != null) {
                span.error(ex);
            }
            log.error("Internal event processing failed topic={} partition={} offset={} errorClass={} message={}",
                    record == null ? null : record.topic(),
                    record == null ? null : record.partition(),
                    record == null ? null : record.offset(),
                    ex.getClass().getName(),
                    ex.getMessage(),
                    ex);
            if (ex instanceof InvalidEventPayloadException invalidEventPayloadException) {
                throw invalidEventPayloadException;
            }
            throw ex;
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
     * Populates MDC keys used by structured logging from the event payload.
     *
     * @param envelope Parsed BaseEvent envelope used as the primary source of correlation identifiers.
     * @return Performs a side effect by updating MDC values for the current thread.
     */
    private static void putMdc(BaseEvent<JsonNode> envelope) {
        Map<String, String> values = new HashMap<>();
        values.put("eventId", envelope == null ? null : envelope.eventId());
        values.put("traceId", envelope == null ? null : envelope.traceId());
        values.put("correlationId", envelope == null ? null : envelope.correlationId());
        values.put("tenantId", tenantIdFromPayload(envelope == null ? null : envelope.data()));
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
}



