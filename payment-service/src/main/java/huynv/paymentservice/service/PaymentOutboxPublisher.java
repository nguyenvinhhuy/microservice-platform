package huynv.paymentservice.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.paymentservice.config.PaymentProperties;
import huynv.paymentservice.domain.PaymentOutbox;
import huynv.paymentservice.domain.PaymentOutboxStatus;
import huynv.paymentservice.repository.PaymentOutboxRepository;
import huynv.paymentservice.util.BackoffUtil;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.TraceFlags;
import io.opentelemetry.api.trace.TraceState;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

/**
 * Publishes unpublished payment outbox records to Kafka with retries and distributed locking.
 */
@Component
public class PaymentOutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(PaymentOutboxPublisher.class);
    private static final io.opentelemetry.api.trace.Tracer tracer = GlobalOpenTelemetry.getTracer("payment-service");

    private final PaymentProperties paymentProperties;
    private final PaymentOutboxTransactionalService paymentOutboxTransactionalService;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final Timer outboxPublishTimer;
    private final io.micrometer.core.instrument.Counter outboxRetryCounter;

    /**
     * Creates an outbox publisher that publishes JSON string payloads to Kafka.
     *
     * @param paymentProperties The payment properties containing kill switch and topic configuration.
     * @param paymentOutboxTransactionalService The transactional service used to claim and update outbox records.
     * @param kafkaTemplate The Kafka template used for publishing to the payment events topic.
     * @param objectMapper The object mapper used to parse envelope headers from JSON payloads.
     * @param paymentOutboxRepository The payment outbox repository used to compute operational backlog metrics.
     * @param meterRegistry The meter registry used to register outbox metrics.
     * @return Initializes an outbox publisher.
     */
    public PaymentOutboxPublisher(
            PaymentProperties paymentProperties,
            PaymentOutboxTransactionalService paymentOutboxTransactionalService,
            @Qualifier("paymentKafkaTemplate") KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper,
            PaymentOutboxRepository paymentOutboxRepository,
            MeterRegistry meterRegistry
    ) {
        this.paymentProperties = paymentProperties;
        this.paymentOutboxTransactionalService = paymentOutboxTransactionalService;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.outboxPublishTimer = Timer.builder("outbox_publish_latency").tag("service", "payment-service").register(meterRegistry);
        this.outboxRetryCounter = meterRegistry.counter("outbox_retry_total", "service", "payment-service");
        io.micrometer.core.instrument.Gauge.builder("outbox_backlog_size", () -> paymentOutboxRepository.countByStatus(PaymentOutboxStatus.NEW))
                .tag("service", "payment-service")
                .register(meterRegistry);
    }

    /**
     * Publishes unpublished outbox events to Kafka in small deterministic batches.
     *
     * @return Publishes due outbox records and updates their publish status.
     */
    @Scheduled(fixedDelayString = "${payment.outbox.publisher-delay-ms:2000}")
    @SchedulerLock(name = "payment-outbox-publisher", lockAtMostFor = "PT30S", lockAtLeastFor = "PT1S")
    public void publishReadyOutbox() {
        if (!paymentProperties.getOutbox().isEnabled()) {
            log.warn("Outbox publishing is disabled by configuration.");
            return;
        }

        OffsetDateTime now = OffsetDateTime.now();
        int batchSize = paymentProperties.getOutbox().getPublisherBatchSize();
        List<PaymentOutbox> ready = paymentOutboxTransactionalService.claimBatch(now, batchSize);
        if (ready.isEmpty()) {
            return;
        }

        String topic = paymentProperties.getKafka().getEventsTopic();
        for (PaymentOutbox outbox : ready) {
            publishOne(topic, outbox);
        }
    }

    /**
     * Publishes a single outbox record to Kafka and updates the retry scheduling on failure.
     *
     * @param topic Kafka topic used for payment domain events.
     * @param outbox Outbox record to publish.
     * @return Publishes the outbox record or schedules a retry on failure.
     */
    private void publishOne(String topic, PaymentOutbox outbox) {
        long startNanos = System.nanoTime();
        try {
            ProducerRecord<String, String> record = new ProducerRecord<>(topic, outbox.getAggregateId(), outbox.getPayload());
            JsonNode envelope = objectMapper.readTree(outbox.getPayload());
            String envelopeTraceId = envelope.path("traceId").asText(null);
            String envelopeEventId = envelope.path("eventId").asText("");
            String parentSpanId = isHex(outbox.getSpanId(), 16) ? outbox.getSpanId() : toSyntheticSpanId(envelopeEventId);
            Context parentContext = toParentContext(envelopeTraceId, parentSpanId);
            Span span = tracer.spanBuilder("outbox.publish")
                    .setSpanKind(SpanKind.PRODUCER)
                    .setParent(parentContext)
                    .startSpan();
            try (Scope scope = span.makeCurrent()) {
                SpanContext spanContext = span.getSpanContext();
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
            kafkaTemplate.send(record).get(10, TimeUnit.SECONDS);
            paymentOutboxTransactionalService.markPublished(outbox.getId(), OffsetDateTime.now());
            } finally {
                span.end();
            }
        } catch (Exception e) {
            outboxRetryCounter.increment();
            OffsetDateTime nextAttemptAt = BackoffUtil.nextAttemptAt(
                    OffsetDateTime.now(),
                    outbox.getPublishAttempts(),
                    Duration.ofSeconds(2),
                    Duration.ofMinutes(5),
                    0.2d
            );
            String message = e.getMessage() == null ? e.getClass().getName() : e.getMessage();
            paymentOutboxTransactionalService.markFailedAttempt(outbox.getId(), nextAttemptAt, message);
            log.warn(
                    "Outbox publish failed. id={}, eventType={}, attempts={}, nextAttemptAt={}, error={}",
                    outbox.getId(),
                    outbox.getEventType(),
                    outbox.getPublishAttempts() + 1,
                    nextAttemptAt,
                    message
            );
        } finally {
            outboxPublishTimer.record(System.nanoTime() - startNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
        }
    }

    /**
     * Creates a parent context from an upstream trace identifier when available.
     *
     * @param traceId Trace identifier from the stored event envelope.
     * @param parentSpanId Parent span identifier used to connect async outbox publishing into the trace.
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
     * @param record Producer record to update.
     * @param key Header key name.
     * @param value Header value that may be null or blank.
     * @return No return; mutates record headers with a safe header value.
     */
    private void addHeader(ProducerRecord<String, String> record, String key, String value) {
        String safe = value == null ? "" : value;
        record.headers().add(key, safe.getBytes(StandardCharsets.UTF_8));
    }
}
