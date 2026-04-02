package huynv.orderservice.event;

import huynv.orderservice.domain.OutboxEvent;
import huynv.orderservice.config.KafkaConfig;
import huynv.orderservice.service.OutboxService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Objects;

/**
 * Publishes only committed outbox rows to Kafka.
 * Delivery guarantee: AT_LEAST_ONCE.
 * Duplicate events are possible by design under retries, so downstream consumers must be idempotent.
 */
@Component
@Slf4j
public class OrderEventProducer {

    private static final io.opentelemetry.api.trace.Tracer tracer = GlobalOpenTelemetry.getTracer("order-service");

    @Qualifier("orderOutboxKafkaTemplate")
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxService outboxService;
    private final MeterRegistry meterRegistry;
    private final ObjectMapper objectMapper;
    private final Timer outboxPublishTimer;
    @Value("${order.outbox.publisher.enabled:true}")
    private boolean outboxPublisherEnabled;
    private final io.micrometer.core.instrument.Counter outboxRetryCounter;

    /**
     * Creates an outbox publisher that reports publish and backlog metrics for operations visibility.
     *
     * @param kafkaTemplate Kafka template used to publish outbox payloads to Kafka.
     * @param outboxService Outbox service used to claim and update outbox row state.
     * @param meterRegistry Meter registry used to register outbox metrics.
     * @param objectMapper Object mapper used to parse envelope headers for Kafka headers.
     * @return Initializes an order outbox publisher instance.
     */
    public OrderEventProducer(@Qualifier("orderOutboxKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
                              OutboxService outboxService,
                              MeterRegistry meterRegistry,
                              ObjectMapper objectMapper) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate");
        this.outboxService = Objects.requireNonNull(outboxService, "outboxService");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.outboxPublishTimer = Timer.builder("outbox_publish_latency")
                .tag("service", "order-service")
                .register(meterRegistry);
        this.outboxRetryCounter = meterRegistry.counter("outbox_retry_total", "service", "order-service");
        io.micrometer.core.instrument.Gauge.builder("outbox_backlog_size", outboxService::outboxBacklogSize)
                .tag("service", "order-service")
                .register(meterRegistry);
    }

    /**
     * Publishes committed outbox events with at-least-once delivery semantics.
     *
     * @return Publishes due outbox events and updates their persisted state for retries and auditing.
     */
    @Scheduled(fixedDelayString = "${outbox.publisher.delay-ms:1000}")
    @SchedulerLock(name = "order-service-outbox-publisher", lockAtMostFor = "PT30S", lockAtLeastFor = "PT2S")
    public void publishOutboxBatch() {
        if (!outboxPublisherEnabled) {
            log.warn("Outbox publisher skipped because order.outbox.publisher.enabled=false");
            return;
        }
        List<OutboxEvent> events = outboxService.lockReadyEvents(50);
        for (OutboxEvent event : events) {
            publishSingle(event);
        }
    }

    /**
     * Sends one outbox event to Kafka with deterministic routing key and trace headers.
     *
     * @param event Outbox row snapshot that contains payload and integration metadata.
     * @return Marks the outbox row as SENT on success or FAILED with retry scheduling on error.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void publishSingle(OutboxEvent event) {
        long startNanos = System.nanoTime();
        try {
            String key = event.getAggregateId();
            JsonNode envelope = objectMapper.readTree(event.getPayload());
            String envelopeTraceId = envelope.path("traceId").asText(null);
            String envelopeEventId = envelope.path("eventId").asText("");
            String parentSpanId = toSyntheticSpanId(envelopeEventId);
            Context parentContext = toParentContext(envelopeTraceId, parentSpanId);
            Span span = tracer.spanBuilder("outbox.publish")
                    .setSpanKind(SpanKind.PRODUCER)
                    .setParent(parentContext)
                    .startSpan();
            try (Scope scope = span.makeCurrent()) {
                SpanContext spanContext = span.getSpanContext();
                ProducerRecord<String, String> record = new ProducerRecord<>(
                        KafkaConfig.ORDER_EVENTS_TOPIC,
                        key,
                        event.getPayload()
                );
                record.headers().add("eventId", nullable(envelope.path("eventId").asText(null)).getBytes());
                record.headers().add("eventType", nullable(envelope.path("eventType").asText(null)).getBytes());
                record.headers().add("dataSchema", nullable(envelope.path("dataSchema").asText(null)).getBytes());
                record.headers().add("traceId", nullable(envelope.path("traceId").asText(null)).getBytes());
                record.headers().add("correlationId", nullable(envelope.path("correlationId").asText(null)).getBytes());
                record.headers().add("causationId", nullable(envelope.path("causationId").asText(null)).getBytes());
                record.headers().add("idempotencyKey", nullable(event.getIdempotencyKey()).getBytes());
                record.headers().add("spanId", spanContext.getSpanId().getBytes(StandardCharsets.UTF_8));
                record.headers().add("parentSpanId", parentSpanId.getBytes(StandardCharsets.UTF_8));

                kafkaTemplate.send(record).get();
                outboxService.markSent(event.getId());
                log.info("Outbox event sent eventId={} type={} aggregateId={}", event.getEventId(), event.getType(), event.getAggregateId());
            } finally {
                span.end();
            }
        } catch (Exception ex) {
            outboxService.markFailed(event.getId(), ex.getMessage());
            outboxRetryCounter.increment();
            meterRegistry.counter("saga.step.failure", "step", "OUTBOX_PUBLISH").increment();
            log.warn("Outbox event publish failed eventId={} type={} reason={}", event.getEventId(), event.getType(), ex.getMessage());
        } finally {
            outboxPublishTimer.record(System.nanoTime() - startNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    /**
     * Converts nullable metadata into non-null header values accepted by Kafka.
     *
     * @param value nullable header value from outbox metadata
     * @return safe header string with empty fallback
     */
    private String nullable(String value) {
        return value == null ? "" : value;
    }

    /**
     * Creates a parent context from an upstream trace identifier when available.
     *
     * @param traceId Trace identifier from the stored event envelope.
     * @param parentSpanId Synthetic parent span identifier used to connect async outbox publishing into the trace.
     * @return Returns a Context containing a remote parent span or a root context when traceId is unavailable.
     */
    private static Context toParentContext(String traceId, String parentSpanId) {
        if (!isHex(traceId, 32) || !isHex(parentSpanId, 16)) {
            return Context.root();
        }
        SpanContext remoteParent = SpanContext.createFromRemoteParent(
                traceId,
                parentSpanId,
                TraceFlags.getSampled(),
                TraceState.getDefault()
        );
        return Context.root().with(Span.wrap(remoteParent));
    }

    /**
     * Derives a stable synthetic span id from a stable string value for async trace stitching.
     *
     * @param input Stable input used to derive the span identifier.
     * @return Returns a 16-hex-character span id derived from the input.
     */
    private static String toSyntheticSpanId(String input) {
        if (input == null || input.isBlank()) {
            return "0000000000000000";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(16);
            for (int i = 0; i < 8; i++) {
                int b = bytes[i] & 0xff;
                String part = Integer.toHexString(b);
                if (part.length() == 1) {
                    hex.append('0');
                }
                hex.append(part);
            }
            return hex.toString();
        } catch (Exception ex) {
            return "0000000000000000";
        }
    }

    /**
     * Validates a lowercase or uppercase hex string has the required length.
     *
     * @param value Candidate hex string.
     * @param length Expected length.
     * @return Returns true when the value is non-blank hex and matches the expected length.
     */
    private static boolean isHex(String value, int length) {
        if (value == null || value.length() != length) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean lower = c >= 'a' && c <= 'f';
            boolean upper = c >= 'A' && c <= 'F';
            if (!(digit || lower || upper)) {
                return false;
            }
        }
        return true;
    }
}
