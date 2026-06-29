package huynv.fileservice.service;

import huynv.fileservice.domain.DownloadTicket;
import huynv.fileservice.exception.ConflictException;
import huynv.fileservice.exception.NotFoundException;
import huynv.fileservice.repository.DownloadTicketRepository;
import huynv.fileservice.security.JwtUserContextExtractor;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.security.core.Authentication;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;

/**
 * Issues and redeems short-lived download tickets bound to tenant and user context.
 */
@Service
public class DownloadTicketService {

    private final DownloadTicketRepository downloadTicketRepository;
    private final JwtUserContextExtractor jwtUserContextExtractor;

    /**
     * Creates a download-ticket service backed by the download_tickets table.
     *
     * @param downloadTicketRepository Repository used to persist and load download tickets.
     * @param jwtUserContextExtractor Extractor used to bind authenticated users to tickets.
     * @return Initializes the download-ticket service.
     */
    public DownloadTicketService(DownloadTicketRepository downloadTicketRepository, JwtUserContextExtractor jwtUserContextExtractor) {
        this.downloadTicketRepository = Objects.requireNonNull(downloadTicketRepository, "downloadTicketRepository");
        this.jwtUserContextExtractor = Objects.requireNonNull(jwtUserContextExtractor, "jwtUserContextExtractor");
    }

    /**
     * Issues a new opaque download ticket bound to the supplied tenant, user, and file identifiers.
     *
     * @param tenantId Tenant identifier that owns the file.
     * @param authentication Current principal, which may be null for public reads.
     * @param fileId File identifier bound to the ticket.
     * @param expiresAt Ticket expiry timestamp.
     * @param singleUse Whether the ticket may be redeemed only once.
     * @return Returns the opaque ticket token to hand back to the caller.
     */
    @Transactional
    public String issue(UUID tenantId, Authentication authentication, UUID fileId, Instant expiresAt, boolean singleUse) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(fileId, "fileId");
        Objects.requireNonNull(expiresAt, "expiresAt");
        UUID userId = authentication == null ? null : jwtUserContextExtractor.tryExtractUserId(authentication);
        String token = UUID.randomUUID() + "." + UUID.randomUUID();
        DownloadTicket ticket = new DownloadTicket(UUID.randomUUID(), sha256(token), tenantId, userId, fileId, singleUse, expiresAt);
        downloadTicketRepository.save(ticket);
        return token;
    }

    /**
     * Loads and validates a ticket for redemption against the current authentication context.
     *
     * @param token Opaque ticket token supplied by the caller.
     * @param authentication Current principal, which may be null for public tickets.
     * @return Returns the validated download ticket.
     */
    @Transactional
    public DownloadTicket redeem(String token, Authentication authentication) {
        Objects.requireNonNull(token, "token");
        DownloadTicket ticket = downloadTicketRepository.findByTokenHash(sha256(token))
                .orElseThrow(() -> new NotFoundException("DOWNLOAD_TICKET_NOT_FOUND", "The download ticket is invalid or expired."));
        if (ticket.isRevoked()) {
            throw new ConflictException("DOWNLOAD_TICKET_REVOKED", "The download ticket has been revoked.");
        }
        if (ticket.getExpiresAt().isBefore(Instant.now())) {
            throw new ConflictException("DOWNLOAD_TICKET_EXPIRED", "The download ticket has expired.");
        }
        UUID currentUserId = authentication == null ? null : jwtUserContextExtractor.tryExtractUserId(authentication);
        if (ticket.getUserId() != null && !Objects.equals(ticket.getUserId(), currentUserId)) {
            ticket.revoke();
            downloadTicketRepository.save(ticket);
            throw new ConflictException("DOWNLOAD_TICKET_SUBJECT_MISMATCH", "The download ticket is not valid for the current user.");
        }
        if (ticket.isSingleUse() && ticket.isUsed()) {
            throw new ConflictException("DOWNLOAD_TICKET_ALREADY_USED", "The download ticket has already been redeemed.");
        }
        if (ticket.isSingleUse()) {
            ticket.markUsed();
            downloadTicketRepository.save(ticket);
        }
        return ticket;
    }

    /**
     * Removes expired download tickets so the table remains small and operationally healthy.
     *
     * @return Performs a side effect by deleting expired ticket rows.
     */
    @Transactional
    @Scheduled(fixedDelayString = "${file-service.storage.download-ticket-ttl:PT5M}")
    @SchedulerLock(name = "file-service-download-ticket-cleanup", lockAtMostFor = "PT10M", lockAtLeastFor = "PT30S")
    public void cleanupExpired() {
        downloadTicketRepository.deleteAll(downloadTicketRepository.findByExpiresAtBefore(Instant.now()));
    }

    /**
     * Hashes an opaque token for storage-safe lookup.
     *
     * @param value Opaque token value.
     * @return Returns the lowercase hexadecimal SHA-256 hash.
     */
    public String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash the download ticket.", ex);
        }
    }
}

