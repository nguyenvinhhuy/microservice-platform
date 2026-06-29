package huynv.fileservice.event;

import huynv.fileservice.domain.MalwareScanStatus;

/**
 * Describes the result returned by a malware scanner implementation.
 *
 * @param malwareScanStatus Malware scan outcome.
 * @param reason Human-readable result detail.
 * @param timedOut Whether the scan attempt timed out.
 * @param checksumBlacklisted Whether the result came from the malicious checksum blacklist.
 * @return Returns an immutable malware-scan verdict.
 */
public record ScanVerdict(
    MalwareScanStatus malwareScanStatus,
    String reason,
    boolean timedOut,
    boolean checksumBlacklisted
) {}
