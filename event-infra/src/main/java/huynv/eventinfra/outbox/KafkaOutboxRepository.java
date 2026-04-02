package huynv.eventinfra.outbox;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Provides persistence operations for Kafka outbox messages used by the outbox publisher.
 */
public interface KafkaOutboxRepository extends JpaRepository<KafkaOutboxMessage, UUID> {

    /**
     * Selects due outbox messages for publishing while locking them to avoid double-send by concurrent publishers.
     *
     * @param statuses Status values eligible for publishing.
     * @param now Current timestamp used to filter due messages.
     * @param pageable Pagination used to cap the claimed batch size.
     * @return Returns a list of due messages locked for update within the current transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from KafkaOutboxMessage m " +
            "where m.status in :statuses and m.dueAt <= :now " +
            "order by m.dueAt asc")
    List<KafkaOutboxMessage> findDueForUpdate(@Param("statuses") Collection<KafkaOutboxStatus> statuses,
                                              @Param("now") OffsetDateTime now,
                                              Pageable pageable);

    /**
     * Selects due outbox messages and stale PROCESSING messages for publishing while locking them to avoid concurrent publishers.
     *
     * @param statuses Status values eligible for publishing.
     * @param processingStatus Status value representing an in-flight publish.
     * @param now Current timestamp used to filter due messages.
     * @param staleBefore Timestamp before which PROCESSING rows are treated as stale.
     * @param pageable Pagination used to cap the claimed batch size.
     * @return Returns a list of due or stale messages locked for update within the current transaction.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from KafkaOutboxMessage m " +
            "where (m.status in :statuses and m.dueAt <= :now) " +
            "or (m.status = :processingStatus and m.updatedAt <= :staleBefore) " +
            "order by m.dueAt asc")
    List<KafkaOutboxMessage> findDueOrStaleProcessingForUpdate(@Param("statuses") Collection<KafkaOutboxStatus> statuses,
                                                              @Param("processingStatus") KafkaOutboxStatus processingStatus,
                                                              @Param("now") OffsetDateTime now,
                                                              @Param("staleBefore") OffsetDateTime staleBefore,
                                                              Pageable pageable);

    /**
     * Counts outbox rows by status for backlog monitoring.
     *
     * @param statuses Status values to include in the count.
     * @return Returns the number of rows matching the provided statuses.
     */
    long countByStatusIn(Collection<KafkaOutboxStatus> statuses);

    /**
     * Counts outbox rows by purpose and status for targeted alerting on specific pipelines.
     *
     * @param purpose Purpose filter.
     * @param statuses Status values to include.
     * @return Returns the number of matching outbox rows.
     */
    long countByPurposeAndStatusIn(KafkaOutboxPurpose purpose, Collection<KafkaOutboxStatus> statuses);
}

