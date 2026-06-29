package huynv.fileservice.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Describes the opaque download ticket returned after a successful ticket issuance request.
 *
 * @param fileId File identifier bound to the ticket.
 * @param token Opaque ticket token to present when downloading the file.
 * @param expiresAt Ticket expiration timestamp.
 * @param singleUse Whether the ticket may be redeemed only once.
 * @return Returns an immutable response payload for download-ticket issuance.
 */
public record DownloadTicketResponse(
        UUID fileId,
        String token,
        Instant expiresAt,
        boolean singleUse
) {
}

