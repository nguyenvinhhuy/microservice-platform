package huynv.fileservice.repository;

import huynv.fileservice.domain.FileAccessAudit;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Provides persistence access for file access audit rows.
 */
public interface FileAccessAuditRepository extends JpaRepository<FileAccessAudit, Long> {
}

