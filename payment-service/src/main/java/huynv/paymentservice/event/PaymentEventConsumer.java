package huynv.paymentservice.event;

import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.paymentservice.exception.NonRetryableMessageException;
import huynv.event.BaseEvent;
import huynv.event.inventory.StockReservedEvent;
import huynv.paymentservice.metrics.PaymentConsumerMetrics;
import huynv.paymentservice.saga.PaymentSagaHandler;
import huynv.paymentservice.util.MdcUtil;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Consumes inbound saga events from Kafka with retry and dead-letter handling configured by the listener container.
 */
@Component
@ConditionalOnProperty(name = "payment.kafka.consumer.enabled", havingValue = "true", matchIfMissing = false)
public class PaymentEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
    private static final io.opentelemetry.api.trace.Tracer tracer = GlobalOpenTelemetry.getTracer("payment-service");

    private final ObjectMapper objectMapper;
    private final PaymentSagaHandler paymentSagaHandler;
    private final PaymentConsumerMetrics paymentConsumerMetrics;
    private final TextMapPropagator propagator;

    /**
     * Creates a payment event consumer that parses JSON payloads and routes them to saga handlers.
     *
     * @param objectMapper ObjectMapper used to parse inbound JSON payloads.
     * @param paymentSagaHandler Saga handler used to process valid events.
     * @param paymentConsumerMetrics Metrics recorder used to measure consumer behavior.
     * @return Initializes a payment event consumer.
     */
    public PaymentEventConsumer(ObjectMapper objectMapper, PaymentSagaHandler paymentSagaHandler, PaymentConsumerMetrics paymentConsumerMetrics) {
        this.objectMapper = objectMapper;
        this.paymentSagaHandler = paymentSagaHandler;
        this.paymentConsumerMetrics = paymentConsumerMetrics;
        this.propagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();
    }

    /**
     * Handles inbound inventory reserved events from the main topic.
     *
     * @param record Kafka record containing the StockReservedEvent JSON payload.
     * @return Parses and processes the event or routes invalid messages to dead-letter.
     */
    @KafkaListener(
            topics = "${payment.kafka.inventory-topic}",
            containerFactory = "paymentKafkaListenerContainerFactory"
    )
    public void onStockReserved(ConsumerRecord<String, String> record) {
        OffsetDateTime start = OffsetDateTime.now();
        try {
            Context parentContext = extractParentContext(record);
            Span span = tracer.spanBuilder("kafka.consume")
                    .setSpanKind(SpanKind.CONSUMER)
                    .setParent(parentContext)
                    .startSpan();
            try (Scope scope = span.makeCurrent()) {
                BaseEvent<StockReservedEvent> event = parse(record.value());
                Map<String, String> context = extractContextHeaders(record, parentContext, span.getSpanContext());
                MdcUtil.runWithContext(context, () -> paymentSagaHandler.handleStockReserved(event));
            } finally {
                span.end();
            }
        } catch (RuntimeException ex) {
            paymentConsumerMetrics.recordError();
            throw ex;
        } finally {
            paymentConsumerMetrics.recordProcessingTime(Duration.between(start, OffsetDateTime.now()));
        }
    }

    /**
     * Handles inbound inventory reserved events from the retry topic.
     *
     * @param record Kafka record containing the StockReservedEvent JSON payload.
     * @return Parses and processes the event or routes invalid messages to dead-letter.
     */
    @KafkaListener(
            topics = "${payment.kafka.retry-topic}",
            containerFactory = "paymentKafkaListenerContainerFactory"
    )
    public void onStockReservedRetry(ConsumerRecord<String, String> record) {
        OffsetDateTime start = OffsetDateTime.now();
        try {
            Context parentContext = extractParentContext(record);
            Span span = tracer.spanBuilder("kafka.consume")
                    .setSpanKind(SpanKind.CONSUMER)
                    .setParent(parentContext)
                    .startSpan();
            try (Scope scope = span.makeCurrent()) {
                BaseEvent<StockReservedEvent> event = parse(record.value());
                Map<String, String> context = extractContextHeaders(record, parentContext, span.getSpanContext());
                MdcUtil.runWithContext(context, () -> paymentSagaHandler.handleStockReserved(event));
            } finally {
                span.end();
            }
        } catch (RuntimeException ex) {
            paymentConsumerMetrics.recordError();
            throw ex;
        } finally {
            paymentConsumerMetrics.recordProcessingTime(Duration.between(start, OffsetDateTime.now()));
        }
    }

    /**
     * Parses raw JSON payload into a schema-version tolerant StockReservedEvent envelope.
     *
     * @param payload Raw JSON payload.
     * @return Returns a parsed StockReservedEvent object.
     */
    private BaseEvent<StockReservedEvent> parse(String payload) {
        try {
            com.fasterxml.jackson.core.type.TypeReference<BaseEvent<com.fasterxml.jackson.databind.JsonNode>> type = new com.fasterxml.jackson.core.type.TypeReference<>() {};
            BaseEvent<com.fasterxml.jackson.databind.JsonNode> raw = objectMapper.readValue(payload, type);
            StockReservedEvent data = objectMapper.convertValue(raw.data(), StockReservedEvent.class);
            return new BaseEvent<>(
                    raw.eventId(),
                    raw.eventType(),
                    raw.source(),
                    raw.eventTime(),
                    raw.aggregateId(),
                    raw.aggregateVersion(),
                    raw.dataSchema(),
                    raw.traceId(),
                    raw.correlationId(),
                    raw.causationId(),
                    data
            );
        } catch (Exception e) {
            log.error("Failed to parse StockReservedEvent payload. payload={}", payload, e);
            throw new NonRetryableMessageException("Invalid StockReservedEvent JSON payload.", e);
        }
    }

    /**
     * Extracts correlation and trace identifiers from Kafka headers for MDC logging.
     *
     * @param record Kafka record containing headers.
     * @param parentContext Extracted parent context from Kafka headers.
     * @param currentSpanContext Current consumer span context created for processing.
     * @return Returns a context map containing correlationId, traceId, spanId, and parentSpanId when available.
     */
    private static Map<String, String> extractContextHeaders(
            ConsumerRecord<String, String> record,
            Context parentContext,
            SpanContext currentSpanContext
    ) {
        Map<String, String> context = new HashMap<>();
        if (record.headers() == null) {
            return context;
        }
        byte[] correlation = headerValue(record, "correlationId");
        if (correlation != null) {
            context.put("correlationId", new String(correlation, StandardCharsets.UTF_8));
        }
        context.put("traceId", currentSpanContext.getTraceId());
        context.put("spanId", currentSpanContext.getSpanId());
        SpanContext parentSpanContext = Span.fromContext(parentContext).getSpanContext();
        if (parentSpanContext.isValid()) {
            context.put("parentSpanId", parentSpanContext.getSpanId());
        }
        context.put("topic", record.topic());
        context.put("partition", String.valueOf(record.partition()));
        context.put("offset", String.valueOf(record.offset()));
        return context;
    }

    /**
     * Extracts the remote parent OpenTelemetry context from Kafka headers.
     *
     * @param record Kafka record containing trace headers.
     * @return Returns the extracted context used as the parent for consumer spans.
     */
    private Context extractParentContext(ConsumerRecord<String, String> record) {
        if (record == null || record.headers() == null) {
            return Context.root();
        }
        return propagator.extract(Context.current(), record.headers(), KafkaHeadersGetter.INSTANCE);
    }

    private static byte[] headerValue(ConsumerRecord<String, String> record, String name) {
        org.apache.kafka.common.header.Header header = record.headers().lastHeader(name);
        return header == null ? null : header.value();
    }

    private enum KafkaHeadersGetter implements TextMapGetter<org.apache.kafka.common.header.Headers> {
        INSTANCE;

        @Override
        public Iterable<String> keys(org.apache.kafka.common.header.Headers headers) {
            if (headers == null) {
                return java.util.List.of();
            }
            java.util.List<String> keys = new ArrayList<>();
            for (org.apache.kafka.common.header.Header header : headers) {
                if (header != null && header.key() != null) {
                    keys.add(header.key());
                }
            }
            return keys;
        }

        @Override
        public String get(org.apache.kafka.common.header.Headers headers, String key) {
            if (headers == null || key == null) {
                return null;
            }
            org.apache.kafka.common.header.Header header = headers.lastHeader(key);
            if (header == null || header.value() == null) {
                return null;
            }
            return new String(header.value(), StandardCharsets.UTF_8);
        }
    }
}

