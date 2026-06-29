package huynv.fileservice.dto;

import jakarta.validation.constraints.Size;

/**
 * Describes the client request used to soft-delete a file record.
 *
 * @param reason Optional human-readable reason recorded in audit logs.
 * @return Returns an immutable request payload for file deletion.
 */
public record DeleteFileRequest(@Size(max = 500) String reason) {
}

