package huynv.paymentservice.service;

import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * Periodically reconciles PROCESSING payments against the payment provider to fix inconsistent states.
 */
@Component
public class PaymentReconciliationJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentReconciliationJob.class);

    private final PaymentTransactionService paymentTransactionService;
    private final Duration processingCutoff;
    private final int batchSize;

    /**
     * Creates a reconciliation job with configurable cutoff and batch size.
     *
     * @param paymentTransactionService Transactional service used to reconcile payments and emit events.
     * @param processingCutoffMinutes Cutoff in minutes to consider a payment as stale processing.
     * @param batchSize Maximum number of payments reconciled per run.
     * @return Initializes a payment reconciliation job.
     */
    public PaymentReconciliationJob(
            PaymentTransactionService paymentTransactionService,
            @org.springframework.beans.factory.annotation.Value("${payment.reconciliation.processing-cutoff-minutes:5}") long processingCutoffMinutes,
            @org.springframework.beans.factory.annotation.Value("${payment.reconciliation.batch-size:50}") int batchSize
    ) {
        this.paymentTransactionService = paymentTransactionService;
        this.processingCutoff = Duration.ofMinutes(processingCutoffMinutes);
        this.batchSize = batchSize;
    }

    /**
     * Reconciles stale processing payments and emits appropriate events for state fixes.
     *
     * @return Performs reconciliation work for a bounded batch of processing payments.
     */
    @Scheduled(fixedDelayString = "${payment.reconciliation.interval-ms:60000}")
    @SchedulerLock(name = "payment-reconciliation", lockAtMostFor = "PT55S", lockAtLeastFor = "PT1S")
    public void reconcile() {
        OffsetDateTime cutoff = OffsetDateTime.now().minus(processingCutoff);
        log.info("Reconciling processing payments older than {}.", cutoff);
        paymentTransactionService.reconcileProcessingPayments(cutoff, batchSize);
    }
}

