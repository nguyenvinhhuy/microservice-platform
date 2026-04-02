package huynv.paymentservice.metrics;

import huynv.paymentservice.repository.PaymentOutboxRepository;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Publishes operational outbox monitoring metrics derived from the local payment_outbox table.
 */
@Component
public class OutboxMonitoringMetrics {

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final AtomicLong oldestEventAgeSeconds;

    /**
     * Creates an outbox monitoring metrics publisher for payment-service.
     *
     * @param meterRegistry Meter registry used to publish outbox monitoring gauges.
     * @param paymentOutboxRepository Outbox repository used to read oldest unsent event timestamps.
     * @return Initializes outbox monitoring gauges for alerting and dashboards.
     */
    public OutboxMonitoringMetrics(MeterRegistry meterRegistry, PaymentOutboxRepository paymentOutboxRepository) {
        Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.paymentOutboxRepository = Objects.requireNonNull(paymentOutboxRepository, "paymentOutboxRepository");
        this.oldestEventAgeSeconds = new AtomicLong(0L);
        Gauge.builder("outbox_oldest_event_age", oldestEventAgeSeconds, AtomicLong::get)
                .description("Age in seconds of the oldest unpublished outbox event.")
                .tag("service", "payment-service")
                .register(meterRegistry);
    }

    /**
     * Periodically refreshes the outbox_oldest_event_age gauge from the database.
     *
     * @return No return; updates the gauge as a side effect.
     */
    @Scheduled(fixedDelayString = "${payment.outbox.metrics.interval-ms:15000}")
    public void refreshOldestAge() {
        OffsetDateTime oldestCreatedAt = paymentOutboxRepository.findOldestUnsentCreatedAt().orElse(null);
        if (oldestCreatedAt == null) {
            oldestEventAgeSeconds.set(0L);
            return;
        }
        long ageSeconds = Math.max(0L, Duration.between(oldestCreatedAt, OffsetDateTime.now()).toSeconds());
        oldestEventAgeSeconds.set(ageSeconds);
    }
}

