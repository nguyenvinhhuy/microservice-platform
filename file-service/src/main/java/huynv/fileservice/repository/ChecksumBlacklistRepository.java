package huynv.fileservice.repository;

import huynv.fileservice.domain.ChecksumBlacklistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * Provides persistence access for malicious checksum blacklist entries.
 */
public interface ChecksumBlacklistRepository extends JpaRepository<ChecksumBlacklistEntry, String> {

    /**
     * Loads an active blacklist entry when the checksum is blocked and not expired.
     *
     * @param checksumSha256 SHA-256 checksum to inspect.
     * @param now Current wall-clock timestamp used to filter expired rows.
     * @return Returns the active blacklist entry when present.
     */
    Optional<ChecksumBlacklistEntry> findByChecksumSha256AndExpiresAtAfterOrExpiresAtIsNull(String checksumSha256, Instant now);
}

