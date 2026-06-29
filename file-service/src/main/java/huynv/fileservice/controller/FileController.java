package huynv.fileservice.controller;

import huynv.fileservice.domain.FileVisibility;
import huynv.fileservice.dto.AbortMultipartUploadRequest;
import huynv.fileservice.dto.CompleteMultipartUploadRequest;
import huynv.fileservice.dto.ConfirmUploadRequest;
import huynv.fileservice.dto.DeleteFileRequest;
import huynv.fileservice.dto.DownloadTicketIssueRequest;
import huynv.fileservice.dto.DownloadTicketResponse;
import huynv.fileservice.dto.FileMetadataResponse;
import huynv.fileservice.dto.InitiateMultipartUploadRequest;
import huynv.fileservice.dto.InitiateMultipartUploadResponse;
import huynv.fileservice.dto.MultipartUploadAbortResponse;
import huynv.fileservice.dto.PresignedDownloadResponse;
import huynv.fileservice.dto.PresignMultipartUploadPartRequest;
import huynv.fileservice.dto.PresignMultipartUploadPartResponse;
import huynv.fileservice.dto.PresignedUploadRequest;
import huynv.fileservice.dto.PresignedUploadResponse;
import huynv.fileservice.service.FileLifecycleService;
import huynv.fileservice.service.MultipartUploadService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Exposes the external file-service REST API for uploads, downloads, metadata, and deletion.
 */
@RestController
@Validated
@RequestMapping("/files")
public class FileController {

    private final FileLifecycleService fileLifecycleService;
    private final MultipartUploadService multipartUploadService;

    /**
     * Creates a controller that delegates file workflows to the lifecycle service.
     *
     * @param fileLifecycleService Lifecycle service implementing file workflows.
     * @return Initializes the file controller.
     */
    public FileController(FileLifecycleService fileLifecycleService, MultipartUploadService multipartUploadService) {
        this.fileLifecycleService = fileLifecycleService;
        this.multipartUploadService = multipartUploadService;
    }

    /**
     * Performs a direct multipart upload through file-service.
     *
     * @param authentication Current authenticated principal.
     * @param file Multipart file to upload.
     * @param category Business category for the file.
     * @param visibility Visibility mode for later reads.
     * @param metadataJson Optional metadata payload.
     * @param correlationId Optional correlation identifier.
     * @param causationId Optional causation identifier.
     * @return Returns the persisted file metadata response.
     */
    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileMetadataResponse uploadDirect(
            Authentication authentication,
            @RequestParam("file") MultipartFile file,
            @RequestParam("category") String category,
            @RequestParam(name = "visibility", defaultValue = "PRIVATE") FileVisibility visibility,
            @RequestParam(name = "metadataJson", required = false) String metadataJson,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(name = "X-Causation-Id", required = false) String causationId
    ) {
        return fileLifecycleService.uploadDirect(authentication, file, category, visibility, metadataJson, correlationId, causationId);
    }

    /**
     * Reserves metadata and generates a pre-signed upload URL.
     *
     * @param authentication Current authenticated principal.
     * @param request Request describing the future upload.
     * @return Returns the pre-signed upload reservation response.
     */
    @PostMapping("/presigned-upload")
    public PresignedUploadResponse createPresignedUpload(Authentication authentication, @Valid @RequestBody PresignedUploadRequest request) {
        return fileLifecycleService.createPresignedUpload(authentication, request);
    }

    /**
     * Initiates a native multipart upload and persists the matching control-plane session.
     *
     * @param authentication Current authenticated principal.
     * @param idempotencyKey Client-provided idempotency key.
     * @param request Multipart upload initiation request.
     * @return Returns the initiated multipart upload response.
     */
    @PostMapping("/multipart/initiate")
    public InitiateMultipartUploadResponse initiateMultipartUpload(
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody InitiateMultipartUploadRequest request
    ) {
        return multipartUploadService.initiate(authentication, idempotencyKey, request);
    }

    /**
     * Issues a pre-signed URL for one multipart upload part.
     *
     * @param authentication Current authenticated principal.
     * @param fileId File identifier reserved for the multipart upload.
     * @param partNumber Part number to upload.
     * @param request Multipart part URL request.
     * @return Returns the pre-signed multipart part upload response.
     */
    @PostMapping("/{fileId}/multipart/parts/{partNumber}/presign")
    public PresignMultipartUploadPartResponse presignMultipartPart(
            Authentication authentication,
            @PathVariable UUID fileId,
            @PathVariable int partNumber,
            @Valid @RequestBody PresignMultipartUploadPartRequest request
    ) {
        return multipartUploadService.presignPart(authentication, fileId, partNumber, request);
    }

    /**
     * Completes an active multipart upload and transitions the reserved file into pending scan.
     *
     * @param authentication Current authenticated principal.
     * @param fileId File identifier reserved for the multipart upload.
     * @param idempotencyKey Client-provided idempotency key.
     * @param request Multipart upload completion request.
     * @param correlationId Optional correlation identifier.
     * @param causationId Optional causation identifier.
     * @return Returns the confirmed file metadata response.
     */
    @PostMapping("/{fileId}/multipart/complete")
    public FileMetadataResponse completeMultipartUpload(
            Authentication authentication,
            @PathVariable UUID fileId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CompleteMultipartUploadRequest request,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(name = "X-Causation-Id", required = false) String causationId
    ) {
        return multipartUploadService.complete(authentication, fileId, idempotencyKey, request, correlationId, causationId);
    }

    /**
     * Aborts an active multipart upload and releases reserved control-plane state.
     *
     * @param authentication Current authenticated principal.
     * @param fileId File identifier reserved for the multipart upload.
     * @param idempotencyKey Client-provided idempotency key.
     * @param request Multipart upload abort request.
     * @return Returns the multipart upload abort response.
     */
    @DeleteMapping("/{fileId}/multipart")
    public MultipartUploadAbortResponse abortMultipartUpload(
            Authentication authentication,
            @PathVariable UUID fileId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody AbortMultipartUploadRequest request
    ) {
        return multipartUploadService.abort(authentication, fileId, idempotencyKey, request);
    }

    /**
     * Confirms a previously reserved pre-signed upload.
     *
     * @param authentication Current authenticated principal.
     * @param fileId File identifier being confirmed.
     * @param idempotencyKey Client-provided idempotency key.
     * @param request Confirmation request payload.
     * @param correlationId Optional correlation identifier.
     * @param causationId Optional causation identifier.
     * @return Returns the confirmed file metadata response.
     */
    @PostMapping("/{fileId}/confirm")
    public FileMetadataResponse confirmUpload(
            Authentication authentication,
            @PathVariable UUID fileId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody ConfirmUploadRequest request,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(name = "X-Causation-Id", required = false) String causationId
    ) {
        return fileLifecycleService.confirmUpload(authentication, fileId, idempotencyKey, request, correlationId, causationId);
    }

    /**
     * Returns file metadata for the current caller.
     *
     * @param authentication Current principal, which may be null for public reads.
     * @param fileId File identifier.
     * @return Returns the file metadata response.
     */
    @GetMapping("/{fileId}")
    public FileMetadataResponse getMetadata(Authentication authentication, @PathVariable UUID fileId) {
        return fileLifecycleService.getMetadata(authentication, fileId);
    }

    /**
     * Lists active files for the authenticated tenant.
     *
     * @param authentication Current authenticated principal.
     * @param pageable Requested page.
     * @return Returns a page of tenant-owned files.
     */
    @GetMapping
    public Page<FileMetadataResponse> list(Authentication authentication, Pageable pageable) {
        return fileLifecycleService.list(authentication, pageable);
    }

    /**
     * Generates a pre-signed download URL for an authorized read.
     *
     * @param authentication Current principal, which may be null for public reads.
     * @param fileId File identifier.
     * @return Returns the pre-signed download response.
     */
    @GetMapping("/{fileId}/presigned-download")
    public PresignedDownloadResponse createPresignedDownload(Authentication authentication, @PathVariable UUID fileId) {
        return fileLifecycleService.createPresignedDownload(authentication, fileId);
    }

    /**
     * Issues a short-lived download ticket for an authorized file read.
     *
     * @param authentication Current principal, which may be null for public reads.
     * @param fileId File identifier.
     * @param request Optional ticket issuance request payload.
     * @return Returns the issued download ticket response.
     */
    @PostMapping("/{fileId}/download-ticket")
    public DownloadTicketResponse createDownloadTicket(
            Authentication authentication,
            @PathVariable UUID fileId,
            @Valid @RequestBody(required = false) DownloadTicketIssueRequest request
    ) {
        return fileLifecycleService.createDownloadTicket(authentication, fileId, request != null && request.singleUse());
    }

    /**
     * Streams the file bytes for an authorized read.
     *
     * @param authentication Current principal, which may be null for public reads.
     * @param fileId File identifier.
     * @return Returns a streaming HTTP response for the file bytes.
     */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> download(Authentication authentication, @PathVariable UUID fileId) {
        return fileLifecycleService.download(authentication, fileId);
    }

    /**
     * Redeems a short-lived download ticket and streams the associated file bytes.
     *
     * @param authentication Current principal, which may be null for public ticket redemption.
     * @param token Opaque ticket token.
     * @return Returns a streaming HTTP response for the file bytes.
     */
    @GetMapping("/download-tickets/{token}/download")
    public ResponseEntity<org.springframework.core.io.InputStreamResource> downloadByTicket(Authentication authentication, @PathVariable String token) {
        return fileLifecycleService.downloadByTicket(authentication, token);
    }

    /**
     * Soft-deletes a file record using idempotency protection.
     *
     * @param authentication Current authenticated principal.
     * @param fileId File identifier.
     * @param idempotencyKey Client-provided idempotency key.
     * @param request Optional delete request payload.
     * @param correlationId Optional correlation identifier.
     * @param causationId Optional causation identifier.
     * @return Returns the deleted file metadata response.
     */
    @DeleteMapping("/{fileId}")
    public FileMetadataResponse delete(
            Authentication authentication,
            @PathVariable UUID fileId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody(required = false) DeleteFileRequest request,
            @RequestHeader(name = "X-Correlation-Id", required = false) String correlationId,
            @RequestHeader(name = "X-Causation-Id", required = false) String causationId
    ) {
        return fileLifecycleService.delete(authentication, fileId, idempotencyKey, request == null ? null : request.reason(), correlationId, causationId);
    }
}

