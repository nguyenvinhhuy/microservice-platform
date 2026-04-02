package huynv.inventoryservice.service;

import huynv.inventoryservice.domain.OutboxEvent;
import huynv.inventoryservice.domain.OutboxStatus;
import huynv.inventoryservice.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

/**
 * Provides transactional boundaries for claiming and updating inventory outbox rows.
 */
@Service
public class InventoryOutboxTransactionalService {

    private final OutboxEventRepository outboxEventRepository;

    /**
     * Creates an outbox transactional service used by publisher workers.
     *
     * @param outboxEventRepository repository used for outbox persistence operations.
     * @return initializes a transactional outbox service.
     */
    public InventoryOutboxTransactionalService(OutboxEventRepository outboxEventRepository) {
        this.outboxEventRepository = outboxEventRepository;
    }

    /**
     * Claims and marks a batch of due outbox rows as PROCESSING.
     *
     * @param now current timestamp used to select due rows.
     * @param limit maximum number of rows to claim.
     * @return returns outbox rows marked as PROCESSING for publication.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<OutboxEvent> claimBatch(OffsetDateTime now, int limit) {
        List<Long> ids = outboxEventRepository.claimReadyIds(now, limit);
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<OutboxEvent> rows = outboxEventRepository.findAllByIdIn(ids);
        for (OutboxEvent row : rows) {
            row.setStatus(OutboxStatus.PROCESSING);
            row.setProcessingStartedAt(now);
        }
        return outboxEventRepository.saveAll(rows);
    }

    /**
     * Marks an outbox row as SENT after Kafka publish succeeds.
     *
     * @param id outbox row identifier to update.
     * @param publishedAt publish timestamp used for auditing.
     * @return no return; persists SENT status and clears processing marker.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long id, OffsetDateTime publishedAt) {
        OutboxEvent row = outboxEventRepository.findById(id).orElseThrow();
        row.setStatus(OutboxStatus.SENT);
        row.setPublishedAt(publishedAt);
        row.setProcessingStartedAt(null);
        row.setLastError(null);
        outboxEventRepository.save(row);
    }

    /**
     * Marks an outbox row as FAILED and schedules a deterministic next attempt.
     *
     * @param id outbox row identifier to update.
     * @param error error text used for diagnostics.
     * @param now current timestamp used to compute next attempt.
     * @return no return; persists FAILED status with incremented retry count.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long id, String error, OffsetDateTime now) {
        OutboxEvent row = outboxEventRepository.findById(id).orElseThrow();
        int nextRetry = row.getRetryCount() + 1;
        long delaySeconds = Math.min(60, (long) Math.pow(2, Math.min(nextRetry, 6)));
        row.setStatus(OutboxStatus.FAILED);
        row.setRetryCount(nextRetry);
        row.setLastError(trim(error, 500));
        row.setNextAttemptAt(now.plusSeconds(delaySeconds));
        row.setProcessingStartedAt(null);
        outboxEventRepository.save(row);
    }

    /**
     * Trims a string to a fixed maximum length for safe database storage.
     *
     * @param input raw string to trim.
     * @param maxLength max allowed length.
     * @return returns trimmed string or null when input is null.
     */
    private String trim(String input, int maxLength) {
        if (input == null) {
            return null;
        }
        return input.length() <= maxLength ? input : input.substring(0, maxLength);
    }
}

