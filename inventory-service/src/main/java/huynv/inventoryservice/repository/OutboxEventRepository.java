package huynv.inventoryservice.repository;

import huynv.inventoryservice.domain.OutboxEvent;
import huynv.inventoryservice.domain.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Persists inventory outbox rows used for reliable Kafka publishing.
 */
@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    /**
     * Claims a batch of due outbox rows for processing with SKIP LOCKED semantics.
     *
     * @param now current timestamp used for due filtering.
     * @param limit maximum rows to claim.
     * @return returns claimed outbox row identifiers.
     */
    @Query(value = """
            select id
            from outbox_events
            where status in ('PENDING','FAILED')
              and next_attempt_at <= :now
            order by created_at
            limit :limit
            for update skip locked
            """, nativeQuery = true)
    List<Long> claimReadyIds(@Param("now") OffsetDateTime now, @Param("limit") int limit);

    /**
     * Loads a batch of outbox rows by identifiers with a pessimistic lock.
     *
     * @param ids outbox identifiers to load.
     * @return returns locked outbox rows for processing.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<OutboxEvent> findAllByIdIn(List<Long> ids);

    /**
     * Counts outbox rows by status for operational monitoring.
     *
     * @param status status to count.
     * @return returns number of outbox rows in the given status.
     */
    long countByStatus(OutboxStatus status);

    /**
     * Finds the oldest created_at timestamp for unsent outbox events.
     *
     * @return Returns the minimum created_at timestamp or empty when the outbox is empty.
     */
    @Query(value = "select min(created_at) from outbox_events where status in ('PENDING','FAILED')", nativeQuery = true)
    Optional<OffsetDateTime> findOldestUnsentCreatedAt();
}
