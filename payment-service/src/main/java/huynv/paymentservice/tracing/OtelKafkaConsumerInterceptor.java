package huynv.paymentservice.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.apache.kafka.clients.consumer.ConsumerInterceptor;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Extracts OpenTelemetry context from Kafka headers for downstream span creation and MDC logging.
 */
public class OtelKafkaConsumerInterceptor implements ConsumerInterceptor<String, String> {

    private final TextMapPropagator propagator;

    /**
     * Creates a consumer interceptor using the global OpenTelemetry propagators.
     *
     * @return Initializes a consumer interceptor that extracts trace context from record headers.
     */
    public OtelKafkaConsumerInterceptor() {
        this.propagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();
    }

    /**
     * Extracts the remote parent span context from headers and writes explicit trace identifiers into headers.
     *
     * @param records Polled consumer records.
     * @return Returns the same records instance after enriching headers with extracted identifiers.
     */
    @Override
    public ConsumerRecords<String, String> onConsume(ConsumerRecords<String, String> records) {
        if (records == null || records.isEmpty()) {
            return records;
        }
        records.forEach(record -> {
            Context extracted = propagator.extract(Context.current(), record.headers(), KafkaHeadersGetter.INSTANCE);
            SpanContext spanContext = Span.fromContext(extracted).getSpanContext();
            if (!spanContext.isValid()) {
                return;
            }
            record.headers().remove("traceId");
            record.headers().remove("parentSpanId");
            record.headers().add("traceId", spanContext.getTraceId().getBytes(StandardCharsets.UTF_8));
            record.headers().add("parentSpanId", spanContext.getSpanId().getBytes(StandardCharsets.UTF_8));
        });
        return records;
    }

    /**
     * Receives commit callback for polled records.
     *
     * @param offsets Committed offsets.
     * @return No return; commits are handled by the Kafka consumer.
     */
    @Override
    public void onCommit(Map<TopicPartition, OffsetAndMetadata> offsets) {
        // No action required.
    }

    /**
     * Closes interceptor resources when the consumer is shut down.
     *
     * @return No return; no resources are held by this interceptor.
     */
    @Override
    public void close() {
        // No resources to close.
    }

    /**
     * Reads consumer configuration for this interceptor.
     *
     * @param configs Consumer configuration map.
     * @return No return; configuration is not required for this interceptor.
     */
    @Override
    public void configure(Map<String, ?> configs) {
        // No configuration required.
    }

    private enum KafkaHeadersGetter implements TextMapGetter<org.apache.kafka.common.header.Headers> {
        INSTANCE;

        @Override
        public Iterable<String> keys(org.apache.kafka.common.header.Headers headers) {
            if (headers == null) {
                return java.util.List.of();
            }
            java.util.List<String> keys = new java.util.ArrayList<>();
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
