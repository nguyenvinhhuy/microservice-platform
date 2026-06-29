package huynv.event.file;

import java.time.Instant;
import java.util.UUID;

/**
 * Describes the asynchronous scanner result reported for an uploaded file object.
 *
 * @param fileId File identifier owned by file-service.
 * @param tenantId Tenant identifier that owns the file.
 * @param malwareStatus Malware result returned by the scanner.
 * @param reason Human-readable result detail from the scanner.
 * @param scanDurationMs Scan duration in milliseconds.
 * @param scannerName Scanner implementation name.
 * @param timedOut Whether the scan timed out before a verdict was produced.
 * @param checksumBlacklisted Whether the verdict came from a malicious checksum blacklist short-circuit.
 * @param scannedAt Timestamp when the scanner completed analysis.
 * @return Returns an immutable payload describing a completed scan result.
 */
public record FileScanCompletedEvent(
        UUID fileId,
        UUID tenantId,
        String malwareStatus,
        String reason,
        long scanDurationMs,
        String scannerName,
        boolean timedOut,
        boolean checksumBlacklisted,
        Instant scannedAt
) {
}

