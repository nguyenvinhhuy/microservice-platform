package huynv.orderservice.tracing;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.apache.kafka.clients.producer.ProducerInterceptor;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Headers;
import org.slf4j.MDC;

import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Injects OpenTelemetry context into Kafka headers for distributed tracing.
 */
public class OtelKafkaProducerInterceptor implements ProducerInterceptor<String, String> {

    private static final String CORRELATION_ID_HEADER = "correlationId";
    private final TextMapPropagator propagator;

    /**
     * Creates a producer interceptor using the global OpenTelemetry propagators.
     *
     * @return Initializes an interceptor that injects trace context into Kafka record headers.
     */
    public OtelKafkaProducerInterceptor() {
        this.propagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();
    }

    /**
     * Injects trace context and correlation id into record headers before the record is sent.
     *
     * @param record Record about to be sent.
     * @return Returns the same record instance with additional headers.
     */
    @Override
    public ProducerRecord<String, String> onSend(ProducerRecord<String, String> record) {
        if (record == null) {
            return null;
        }
        Headers headers = record.headers();
        propagator.inject(Context.current(), headers, OtelKafkaProducerInterceptor::setHeader);

        String correlationId = MDC.get("correlationId");
        if (correlationId != null && !correlationId.isBlank()) {
            headers.remove(CORRELATION_ID_HEADER);
            headers.add(CORRELATION_ID_HEADER, correlationId.getBytes(StandardCharsets.UTF_8));
        }
        return record;
    }

    /**
     * Receives acknowledgment callback from the Kafka client after a record has been sent.
     *
     * @param metadata Record metadata returned by the broker.
     * @param exception Exception returned by the Kafka client, if any.
     * @return No return; callback is used for side effects only.
     */
    @Override
    public void onAcknowledgement(RecordMetadata metadata, Exception exception) {
        // Intentionally empty: tracing context injection is handled in onSend.
    }

    /**
     * Closes interceptor resources when the producer is shut down.
     *
     * @return No return; no resources are held by this interceptor.
     */
    @Override
    public void close() {
        // No resources to close.
    }

    /**
     * Reads producer configuration for this interceptor.
     *
     * @param configs Producer configuration map.
     * @return No return; configuration is not required for this interceptor.
     */
    @Override
    public void configure(Map<String, ?> configs) {
        // No configuration required.
    }

    /**
     * Writes a single header key/value entry into Kafka record headers.
     *
     * @param headers Kafka headers to update.
     * @param key Header key name.
     * @param value Header value.
     * @return No return; mutates headers as a side effect.
     */
    private static void setHeader(Headers headers, String key, String value) {
        if (headers == null || key == null || value == null) {
            return;
        }
        headers.remove(key);
        headers.add(key, value.getBytes(StandardCharsets.UTF_8));
    }
}

