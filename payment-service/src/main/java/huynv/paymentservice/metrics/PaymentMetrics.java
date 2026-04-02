package huynv.paymentservice.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Provides Micrometer metrics for payment processing outcomes and latency.
 */
@Component
public class PaymentMetrics {

    private final Counter paymentRequestsTotal;
    private final Counter paymentSuccessTotal;
    private final Counter paymentFailedTotal;
    private final Timer paymentLatencySeconds;
    private final Timer paymentLatency;

    /**
     * Creates a metrics helper that registers counters and timers in the provided meter registry.
     *
     * @param meterRegistry Meter registry used to register and publish metrics.
     * @return Initializes payment metrics counters and timers.
     */
    public PaymentMetrics(MeterRegistry meterRegistry) {
        this.paymentRequestsTotal = meterRegistry.counter("payment_requests_total");
        this.paymentSuccessTotal = meterRegistry.counter("payment_success_total");
        this.paymentFailedTotal = meterRegistry.counter("payment_failed_total");
        this.paymentLatencySeconds = Timer.builder("payment_latency_seconds")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .distributionStatisticExpiry(Duration.ofMinutes(10))
                .register(meterRegistry);
        this.paymentLatency = Timer.builder("payment_latency")
                .publishPercentiles(0.5, 0.95, 0.99)
                .publishPercentileHistogram()
                .distributionStatisticExpiry(Duration.ofMinutes(10))
                .register(meterRegistry);
    }

    /**
     * Records that a payment request has been received for processing.
     *
     * @return Increments the payment requests counter.
     */
    public void recordRequest() {
        paymentRequestsTotal.increment();
    }

    /**
     * Records that a payment has been successfully processed.
     *
     * @return Increments the payment success counter.
     */
    public void recordSuccess() {
        paymentSuccessTotal.increment();
    }

    /**
     * Records that a payment has failed to process.
     *
     * @return Increments the payment failed counter.
     */
    public void recordFailure() {
        paymentFailedTotal.increment();
    }

    /**
     * Records a payment processing latency duration.
     *
     * @param duration Duration measured for payment processing.
     * @return Records the duration in the payment latency timer.
     */
    public void recordLatency(Duration duration) {
        paymentLatencySeconds.record(duration);
        paymentLatency.record(duration);
    }
}
