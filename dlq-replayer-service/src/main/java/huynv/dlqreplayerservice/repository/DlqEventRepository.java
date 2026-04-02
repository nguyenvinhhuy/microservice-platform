package huynv.dlqreplayerservice.repository;

import huynv.dlqreplayerservice.model.DlqEvent;
import huynv.dlqreplayerservice.model.DlqEventStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Provides persistence access for DLQ event rows used by the replay workflow.
 */
public interface DlqEventRepository extends JpaRepository<DlqEvent, Long> {

    /**
     * Loads DLQ events by status for inspection.
     *
     * @param status Status filter applied to DLQ events.
     * @param pageable Pagination information.
     * @return Returns a page of DLQ events.
     */
    Page<DlqEvent> findByStatus(DlqEventStatus status, Pageable pageable);
}

