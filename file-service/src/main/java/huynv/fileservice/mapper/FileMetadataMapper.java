package huynv.fileservice.mapper;

import huynv.fileservice.domain.FileRecord;
import huynv.fileservice.dto.FileMetadataResponse;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Maps persisted file metadata entities into external response DTOs.
 */
@Component
public class FileMetadataMapper {

    /**
     * Converts a persisted file record into the external metadata response contract.
     *
     * @param fileRecord Persisted file metadata record.
     * @return Returns the external metadata response contract.
     */
    public FileMetadataResponse toResponse(FileRecord fileRecord) {
        Objects.requireNonNull(fileRecord, "fileRecord");
        return new FileMetadataResponse(
                fileRecord.getId(),
                fileRecord.getTenantId(),
                fileRecord.getOwnerUserId(),
                fileRecord.getCategory(),
                fileRecord.getBucket(),
                fileRecord.getObjectKey(),
                fileRecord.getOriginalFilename(),
                fileRecord.getContentType(),
                fileRecord.getSizeBytes(),
                fileRecord.getChecksumSha256(),
                fileRecord.getStatus(),
                fileRecord.getVisibility(),
                fileRecord.getMalwareScanStatus(),
                fileRecord.getMetadataJson(),
                fileRecord.getCreatedAt(),
                fileRecord.getUpdatedAt(),
                fileRecord.getDeletedAt()
        );
    }
}

