package huynv.paymentservice.service;

import huynv.paymentservice.domain.PaymentOutbox;
import huynv.paymentservice.repository.PaymentOutboxRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Provides transactional outbox operations for two-phase outbox publishing.
 */
@Service
public class PaymentOutboxTransactionalService {

    private final PaymentOutboxRepository paymentOutboxRepository;

    /**
     * Creates a transactional service for claiming and updating payment outbox records.
     *
     * @param paymentOutboxRepository Repository used to claim and update outbox records.
     * @return Initializes a transactional outbox service.
     */
    public PaymentOutboxTransactionalService(PaymentOutboxRepository paymentOutboxRepository) {
        this.paymentOutboxRepository = paymentOutboxRepository;
    }

    /**
     * Claims a batch of outbox records for publishing using SKIP LOCKED and marks them PROCESSING.
     *
     * @param now Current timestamp used to claim and mark outbox records.
     * @param batchSize Maximum number of outbox records to claim.
     * @return Returns claimed outbox records in PROCESSING status.
     */
    @Transactional
    public List<PaymentOutbox> claimBatch(OffsetDateTime now, int batchSize) {
        List<Long> ids = paymentOutboxRepository.claimReadyIds(now, batchSize);
        if (ids.isEmpty()) {
            return List.of();
        }

        List<PaymentOutbox> records = paymentOutboxRepository.findAllByIdIn(ids);
        for (PaymentOutbox outbox : records) {
            outbox.markProcessing(now);
        }
        paymentOutboxRepository.saveAll(records);
        return records;
    }

    /**
     * Marks the specified outbox record as published.
     *
     * @param outboxId Outbox record identifier.
     * @param publishedAt Publish timestamp.
     * @return Updates the outbox record status to PUBLISHED.
     */
    @Transactional
    public void markPublished(Long outboxId, OffsetDateTime publishedAt) {
        PaymentOutbox outbox = paymentOutboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalStateException("Outbox record not found for id=" + outboxId + "."));
        outbox.markPublished(publishedAt);
        paymentOutboxRepository.save(outbox);
    }

    /**
     * Records a failed outbox publish attempt and schedules the next attempt.
     *
     * @param outboxId Outbox record identifier.
     * @param nextAttemptAt Timestamp for the next publish attempt.
     * @param error Error message stored for diagnostics.
     * @return Updates the outbox record retry scheduling state.
     */
    @Transactional
    public void markFailedAttempt(Long outboxId, OffsetDateTime nextAttemptAt, String error) {
        PaymentOutbox outbox = paymentOutboxRepository.findById(outboxId)
                .orElseThrow(() -> new IllegalStateException("Outbox record not found for id=" + outboxId + "."));
        outbox.markFailedAttempt(nextAttemptAt, error);
        paymentOutboxRepository.save(outbox);
    }
}

