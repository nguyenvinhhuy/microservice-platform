package huynv.paymentservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Records consumer-side metrics for Kafka processing latency and error counts.
 */
@Component
public class PaymentConsumerMetrics {

    private final Timer processingTime;
    private final Timer eventProcessingLatency;
    private final Timer kafkaConsumerProcessingLatency;
    private final Counter errorsTotal;

    /**
     * Creates consumer metrics using the provided meter registry.
     *
     * @param meterRegistry Meter registry used to register and publish metrics.
     * @return Initializes consumer metrics instruments.
     */
    public PaymentConsumerMetrics(MeterRegistry meterRegistry) {
        this.processingTime = meterRegistry.timer("payment_consumer_processing_time");
        this.eventProcessingLatency = Timer.builder("event_processing_latency").tag("service", "payment-service").register(meterRegistry);
        this.kafkaConsumerProcessingLatency = Timer.builder("kafka_consumer_processing_latency").tag("service", "payment-service").register(meterRegistry);
        this.errorsTotal = meterRegistry.counter("payment_consumer_errors_total");
    }

    /**
     * Records one consumer processing time measurement.
     *
     * @param duration Duration of processing for one message.
     * @return Records the duration in the processing time timer.
     */
    public void recordProcessingTime(Duration duration) {
        processingTime.record(duration);
        eventProcessingLatency.record(duration);
        kafkaConsumerProcessingLatency.record(duration);
    }

    /**
     * Records one consumer processing error.
     *
     * @return Increments the consumer errors counter.
     */
    public void recordError() {
        errorsTotal.increment();
    }
}
