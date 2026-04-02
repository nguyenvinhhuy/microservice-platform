package huynv.orderservice.repository;

import huynv.orderservice.domain.OutboxEvent;
import huynv.orderservice.domain.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(value = "SELECT * " +
            "FROM outbox_events " +
            "WHERE status IN ('PENDING','FAILED') " +
            "AND next_attempt_at <= NOW() " +
            "ORDER BY created_at " +
            "LIMIT :limit " +
            "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> lockReadyBatch(@Param("limit") int limit);

    long countByStatus(OutboxStatus status);

    @Query(value = "select min(created_at) from outbox_events where status in ('PENDING','FAILED')", nativeQuery = true)
    OffsetDateTime findOldestUnsentCreatedAt();
}
