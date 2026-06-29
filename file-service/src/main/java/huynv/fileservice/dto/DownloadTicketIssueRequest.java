package huynv.fileservice.dto;

/**
 * Describes the client request used to issue a short-lived download ticket for a file.
 *
 * @param singleUse Whether the issued download ticket may be redeemed only once.
 * @return Returns an immutable request payload for download-ticket issuance.
 */
public record DownloadTicketIssueRequest(boolean singleUse) {
}

