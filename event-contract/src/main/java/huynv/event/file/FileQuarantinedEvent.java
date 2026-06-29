package huynv.event.file;

import java.time.Instant;
import java.util.UUID;

/**
 * Describes a file object that became quarantined because scanning reported malware or an unrecoverable scan issue.
 *
 * @param fileId File identifier owned by file-service.
 * @param tenantId Tenant identifier that owns the file.
 * @param ownerUserId User identifier that owns the file.
 * @param malwareStatus Malware status reported by the scanner pipeline.
 * @param reason Human-readable quarantine reason suitable for auditing.
 * @param quarantinedAt Timestamp when the file became inaccessible.
 * @return Returns an immutable payload describing a quarantined file.
 */
public record FileQuarantinedEvent(
        UUID fileId,
        UUID tenantId,
        UUID ownerUserId,
        String malwareStatus,
        String reason,
        Instant quarantinedAt
) {
}

