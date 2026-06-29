package huynv.event.file;

import java.time.Instant;
import java.util.UUID;

/**
 * Describes a file object that was soft-deleted and removed from normal reads.
 *
 * @param fileId File identifier owned by file-service.
 * @param tenantId Tenant identifier that owns the file.
 * @param ownerUserId User identifier that owns the file.
 * @param bucket Storage bucket that previously contained the object.
 * @param objectKey Stable object key of the deleted object.
 * @param deletedAt Timestamp when the file was deleted.
 * @return Returns an immutable payload describing a deleted file.
 */
public record FileDeletedEvent(
        UUID fileId,
        UUID tenantId,
        UUID ownerUserId,
        String bucket,
        String objectKey,
        Instant deletedAt
) {
}

