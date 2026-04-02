package huynv.paymentservice.repository;

import huynv.paymentservice.domain.Payment;
import huynv.paymentservice.domain.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.springframework.data.domain.Pageable;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides persistence access for payment aggregates.
 *
 * @return Enables tenant-safe payment persistence by idempotency and payment identifiers.
 */
@Repository
public interface PaymentRepository extends JpaRepository<Payment, UUID> {

    /**
     * Loads an existing payment by idempotency key to enforce request idempotency.
     *
     * @param idempotencyKey Idempotency key used by payment processing requests.
     * @return Existing payment when the idempotency key is already processed.
     */
    Optional<Payment> findByIdempotencyKey(String idempotencyKey);

    /**
     * Loads payments in a specific status with updatedAt older than a cutoff timestamp.
     *
     * @param status Payment status used for filtering.
     * @param cutoff Cutoff timestamp for updatedAt filtering.
     * @param pageable Paging configuration used to limit batch size deterministically.
     * @return Returns a batch of payments matching the status and cutoff.
     */
    List<Payment> findByStatusAndUpdatedAtBefore(PaymentStatus status, OffsetDateTime cutoff, Pageable pageable);
}
