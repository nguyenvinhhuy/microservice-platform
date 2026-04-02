package huynv.paymentservice.saga;

import huynv.paymentservice.service.PaymentTransactionService;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Detects stalled saga steps where payment remains PROCESSING beyond a timeout and emits failure events.
 */
@Component
public class PaymentSagaTimeoutMonitor {

    private static final Logger log = LoggerFactory.getLogger(PaymentSagaTimeoutMonitor.class);

    private final PaymentTransactionService paymentTransactionService;
    private final Duration timeout;
    private final int batchSize;

    /**
     * Creates a timeout monitor for stuck payment processing states.
     *
     * @param paymentTransactionService Transactional service used to mark timed out payments and emit events.
     * @param timeoutMinutes Timeout in minutes to consider a processing payment as stalled.
     * @param batchSize Maximum number of payments handled per run.
     * @return Initializes a payment saga timeout monitor.
     */
    public PaymentSagaTimeoutMonitor(
            PaymentTransactionService paymentTransactionService,
            @org.springframework.beans.factory.annotation.Value("${payment.saga-timeout.minutes:15}") long timeoutMinutes,
            @org.springframework.beans.factory.annotation.Value("${payment.saga-timeout.batch-size:50}") int batchSize
    ) {
        this.paymentTransactionService = paymentTransactionService;
        this.timeout = Duration.ofMinutes(timeoutMinutes);
        this.batchSize = batchSize;
    }

    /**
     * Marks timed out processing payments as failed and emits PaymentFailedEvent to allow saga continuation.
     *
     * @return Performs timeout handling work for a bounded batch of processing payments.
     */
    @Scheduled(fixedDelayString = "${payment.saga-timeout.interval-ms:60000}")
    @SchedulerLock(name = "payment-saga-timeout", lockAtMostFor = "PT55S", lockAtLeastFor = "PT1S")
    public void detectAndFailStuckPayments() {
        OffsetDateTime cutoff = OffsetDateTime.now().minus(timeout);
        log.warn("Failing processing payments older than {} due to saga timeout.", cutoff);
        paymentTransactionService.failProcessingTimeouts(cutoff, batchSize, "SAGA_TIMEOUT");
    }
}

