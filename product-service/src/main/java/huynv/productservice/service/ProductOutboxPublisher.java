package huynv.productservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.productservice.config.KafkaTopicConfig;
import huynv.productservice.model.OutboxEvent;
import huynv.productservice.model.OutboxStatus;
import huynv.productservice.repository.OutboxEventRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
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
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Publishes committed product outbox rows to Kafka with at-least-once delivery semantics.
 */
@Component
@Slf4j
public class ProductOutboxPublisher {

    private static final io.opentelemetry.api.trace.Tracer tracer = GlobalOpenTelemetry.getTracer("product-service");

    private final ProductOutboxTransactionalService outboxTransactionalService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Timer outboxPublishTimer;
    private final io.micrometer.core.instrument.Counter outboxRetryCounter;

    @Value("${feature.product.outbox.enabled:true}")
    private boolean outboxEnabled;

    @Value("${product.outbox.publisher-delay-ms:2000}")
    private long publisherDelayMs;

    @Value("${product.outbox.publisher-batch-size:50}")
    private int publisherBatchSize;

    /**
     * Creates a product outbox publisher.
     *
     * @param outboxTransactionalService The transactional service used to claim and update outbox rows.
     * @param kafkaTemplate The Kafka template used to publish JSON payloads to Kafka.
     * @param objectMapper The object mapper used to parse envelope headers from JSON payloads.
     * @param outboxEventRepository The outbox repository used to compute operational backlog metrics.
     * @param meterRegistry The meter registry used to register outbox metrics.
     * @return Initializes a product outbox publisher.
     */
    public ProductOutboxPublisher(
            ProductOutboxTransactionalService outboxTransactionalService,
            @Qualifier("productOutboxKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            OutboxEventRepository outboxEventRepository,
            MeterRegistry meterRegistry
    ) {
        this.outboxTransactionalService = outboxTransactionalService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.outboxPublishTimer = Timer.builder("outbox_publish_latency").tag("service", "product-service").register(meterRegistry);
        this.outboxRetryCounter = meterRegistry.counter("outbox_retry_total", "service", "product-service");
        io.micrometer.core.instrument.Gauge.builder(
                        "outbox_backlog_size",
                        () -> outboxEventRepository.countByStatus(OutboxStatus.PENDING) + outboxEventRepository.countByStatus(OutboxStatus.FAILED)
                )
                .tag("service", "product-service")
                .register(meterRegistry);
    }

    /**
     * Publishes due product outbox events in deterministic batches and updates row state after publish attempts.
     *
     * @return Publishes committed rows and persists publish status for retries.
     */
    @Scheduled(fixedDelayString = "${product.outbox.publisher-delay-ms:2000}")
    @SchedulerLock(name = "product-service-outbox-publisher", lockAtMostFor = "PT30S", lockAtLeastFor = "PT2S")
    public void publishDueOutbox() {
        if (!outboxEnabled) {
            log.warn("Outbox publisher skipped because feature.product.outbox.enabled=false");
            return;
        }
        OffsetDateTime now = OffsetDateTime.now();
        List<OutboxEvent> batch = outboxTransactionalService.claimBatch(now, publisherBatchSize);
        for (OutboxEvent row : batch) {
            publishSingle(row);
        }
    }

    /**
     * Publishes a single product outbox row payload to Kafka and finalizes row status.
     *
     * @param row claimed outbox row marked as PROCESSING.
     * @return Marks the row as SENT on success or FAILED on publish error.
     */
    private void publishSingle(OutboxEvent row) {
        OffsetDateTime now = OffsetDateTime.now();
        long startNanos = System.nanoTime();
        try {
            JsonNode envelope = objectMapper.readTree(row.getPayload());
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
                    KafkaTopicConfig.PRODUCT_EVENTS_TOPIC,
                    row.getAggregateId(),
                    row.getPayload()
            );
            addHeader(record, "eventId", envelope.path("eventId").asText(""));
            addHeader(record, "eventType", envelope.path("eventType").asText(""));
            addHeader(record, "dataSchema", envelope.path("dataSchema").asText(""));
            addHeader(record, "source", envelope.path("source").asText(""));
            addHeader(record, "eventTime", envelope.path("eventTime").asText(""));
            addHeader(record, "aggregateId", envelope.path("aggregateId").asText(""));
            addHeader(record, "aggregateVersion", envelope.path("aggregateVersion").asText(""));
            addHeader(record, "traceId", envelope.path("traceId").asText(""));
            addHeader(record, "correlationId", envelope.path("correlationId").asText(""));
            addHeader(record, "causationId", envelope.path("causationId").asText(""));
            addHeader(record, "spanId", spanContext.getSpanId());
            addHeader(record, "parentSpanId", parentSpanId);

            kafkaTemplate.send(record).get();
            outboxTransactionalService.markSent(row.getId(), now);
            } finally {
                span.end();
            }
        } catch (Exception ex) {
            outboxTransactionalService.markFailed(row.getId(), ex.getMessage(), now);
            outboxRetryCounter.increment();
            log.warn("Product outbox publish failed id={} eventId={} type={} reason={}", row.getId(), row.getEventId(), row.getType(), ex.getMessage());
        } finally {
            outboxPublishTimer.record(System.nanoTime() - startNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
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

    /**
     * Adds a UTF-8 encoded header to a producer record using an empty-string fallback.
     *
     * @param record producer record to update.
     * @param key header key name.
     * @param value header value that may be null or blank.
     * @return no return; mutates record headers with a safe header value.
     */
    private void addHeader(ProducerRecord<String, String> record, String key, String value) {
        String safe = value == null ? "" : value;
        record.headers().add(key, safe.getBytes(StandardCharsets.UTF_8));
    }
}
