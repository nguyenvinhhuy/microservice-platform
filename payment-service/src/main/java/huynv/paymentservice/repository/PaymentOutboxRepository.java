package huynv.paymentservice.repository;

import huynv.paymentservice.domain.PaymentOutbox;
import huynv.paymentservice.domain.PaymentOutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Provides persistence access for payment outbox records used for reliable event publishing.
 */
@Repository
public interface PaymentOutboxRepository extends JpaRepository<PaymentOutbox, Long> {

    /**
     * Claims a batch of outbox record identifiers for two-phase publishing using SKIP LOCKED.
     *
     * @param now Current timestamp used to select ready retry candidates.
     * @param limit Maximum number of identifiers to claim.
     * @return Returns outbox record identifiers claimed for processing.
     */
    @Query(
            value = """
                    select id
                    from payment_outbox
                    where status = 'NEW'
                      and (next_attempt_at is null or next_attempt_at <= :now)
                    order by created_at asc
                    limit :limit
                    for update skip locked
                    """,
            nativeQuery = true
    )
    List<Long> claimReadyIds(OffsetDateTime now, int limit);

    /**
     * Loads a batch of outbox records by identifiers with a pessimistic write lock.
     *
     * @param ids Outbox identifiers to load.
     * @return Returns outbox records for the specified identifiers.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<PaymentOutbox> findAllByIdIn(List<Long> ids);

    /**
     * Counts payment outbox records by status for operational monitoring.
     *
     * @param status Status value to count.
     * @return Returns number of outbox records in the given status.
     */
    long countByStatus(PaymentOutboxStatus status);

    /**
     * Finds the oldest created_at timestamp for unpublished outbox records.
     *
     * @return Returns the minimum created_at timestamp or empty when the outbox is empty.
     */
    @Query(value = "select min(created_at) from payment_outbox where status = 'NEW'", nativeQuery = true)
    Optional<OffsetDateTime> findOldestUnsentCreatedAt();
}
