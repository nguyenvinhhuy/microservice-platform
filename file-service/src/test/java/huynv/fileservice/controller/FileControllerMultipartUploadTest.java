package huynv.fileservice.controller;

import huynv.fileservice.domain.MultipartUploadStatus;
import huynv.fileservice.dto.AbortMultipartUploadRequest;
import huynv.fileservice.dto.CompleteMultipartUploadRequest;
import huynv.fileservice.dto.FileMetadataResponse;
import huynv.fileservice.dto.InitiateMultipartUploadRequest;
import huynv.fileservice.dto.InitiateMultipartUploadResponse;
import huynv.fileservice.dto.MultipartCompletedPartRequest;
import huynv.fileservice.dto.MultipartUploadAbortResponse;
import huynv.fileservice.dto.PresignMultipartUploadPartRequest;
import huynv.fileservice.dto.PresignMultipartUploadPartResponse;
import huynv.fileservice.service.FileLifecycleService;
import huynv.fileservice.service.MultipartUploadService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Verifies multipart upload controller endpoints delegate to the multipart upload service.
 */
class FileControllerMultipartUploadTest {

    private final FileLifecycleService fileLifecycleService = mock(FileLifecycleService.class);
    private final MultipartUploadService multipartUploadService = mock(MultipartUploadService.class);
    private final FileController fileController = new FileController(fileLifecycleService, multipartUploadService);

    /**
     * Verifies multipart initiation delegates to the multipart upload service.
     *
     * @return Performs assertions against the multipart initiation response.
     */
    @Test
    void initiateMultipartUploadDelegatesToService() {
        Authentication authentication = mock(Authentication.class);
        UUID fileId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        InitiateMultipartUploadRequest request = new InitiateMultipartUploadRequest("invoice", "invoice.pdf", "application/pdf", 1024L, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", huynv.fileservice.domain.FileVisibility.PRIVATE, null);
        when(multipartUploadService.initiate(any(), anyString(), eq(request)))
                .thenReturn(new InitiateMultipartUploadResponse(fileId, sessionId, "upload-123", "bucket", "object-key", Instant.parse("2026-05-01T12:00:00Z")));

        InitiateMultipartUploadResponse response = fileController.initiateMultipartUpload(authentication, "idem-1", request);

        assertThat(response.fileId()).isEqualTo(fileId);
        assertThat(response.uploadSessionId()).isEqualTo(sessionId);
    }

    /**
     * Verifies multipart part URL issuance delegates to the multipart upload service.
     *
     * @return Performs assertions against the multipart part upload response.
     */
    @Test
    void presignMultipartPartDelegatesToService() {
        Authentication authentication = mock(Authentication.class);
        UUID fileId = UUID.randomUUID();
        PresignMultipartUploadPartRequest request = new PresignMultipartUploadPartRequest("upload-123", null, 5242880L);
        when(multipartUploadService.presignPart(any(), eq(fileId), anyInt(), eq(request)))
                .thenReturn(new PresignMultipartUploadPartResponse(fileId, UUID.randomUUID(), "upload-123", 1, "https://example/upload", Map.of(), Instant.parse("2026-05-01T12:00:00Z")));

        PresignMultipartUploadPartResponse response = fileController.presignMultipartPart(authentication, fileId, 1, request);

        assertThat(response.partNumber()).isEqualTo(1);
        assertThat(response.uploadId()).isEqualTo("upload-123");
    }

    /**
     * Verifies multipart completion delegates to the multipart upload service.
     *
     * @return Performs assertions against the completed file metadata response.
     */
    @Test
    void completeMultipartUploadDelegatesToService() {
        Authentication authentication = mock(Authentication.class);
        UUID fileId = UUID.randomUUID();
        CompleteMultipartUploadRequest request = new CompleteMultipartUploadRequest(
                "upload-123",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                null,
                List.of(new MultipartCompletedPartRequest(1, "etag-1", null, 1024L))
        );
        when(multipartUploadService.complete(any(), eq(fileId), anyString(), eq(request), any(), any()))
                .thenReturn(new FileMetadataResponse(fileId, UUID.randomUUID(), UUID.randomUUID(), "invoice", "bucket", "object-key", "invoice.pdf", "application/pdf", 1024L, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", huynv.fileservice.domain.FileStatus.PENDING_SCAN, huynv.fileservice.domain.FileVisibility.PRIVATE, huynv.fileservice.domain.MalwareScanStatus.PENDING, null, Instant.now(), Instant.now(), null));

        FileMetadataResponse response = fileController.completeMultipartUpload(authentication, fileId, "idem-1", request, null, null);

        assertThat(response.id()).isEqualTo(fileId);
        assertThat(response.status()).isEqualTo(huynv.fileservice.domain.FileStatus.PENDING_SCAN);
    }

    /**
     * Verifies multipart abort delegates to the multipart upload service.
     *
     * @return Performs assertions against the abort response.
     */
    @Test
    void abortMultipartUploadDelegatesToService() {
        Authentication authentication = mock(Authentication.class);
        UUID fileId = UUID.randomUUID();
        AbortMultipartUploadRequest request = new AbortMultipartUploadRequest("upload-123");
        when(multipartUploadService.abort(any(), eq(fileId), anyString(), eq(request)))
                .thenReturn(new MultipartUploadAbortResponse(fileId, UUID.randomUUID(), "upload-123", MultipartUploadStatus.ABORTED));

        MultipartUploadAbortResponse response = fileController.abortMultipartUpload(authentication, fileId, "idem-1", request);

        assertThat(response.fileId()).isEqualTo(fileId);
        assertThat(response.status()).isEqualTo(MultipartUploadStatus.ABORTED);
    }
}

