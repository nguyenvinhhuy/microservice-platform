package huynv.fileservice.repository;

import huynv.fileservice.domain.DownloadTicket;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Provides persistence access for one-time and short-lived download tickets.
 */
public interface DownloadTicketRepository extends JpaRepository<DownloadTicket, UUID> {

    /**
     * Loads a single ticket by its hashed token value.
     *
     * @param tokenHash SHA-256 hash of the opaque ticket token.
     * @return Returns the ticket when present.
     */
    Optional<DownloadTicket> findByTokenHash(String tokenHash);

    /**
     * Lists expired tickets for cleanup jobs.
     *
     * @param expiresAt Expiration threshold.
     * @return Returns tickets whose expiry timestamp is before the threshold.
     */
    List<DownloadTicket> findByExpiresAtBefore(Instant expiresAt);
}

