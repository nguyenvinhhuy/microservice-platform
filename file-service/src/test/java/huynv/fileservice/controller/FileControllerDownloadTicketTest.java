package huynv.fileservice.controller;

import huynv.fileservice.dto.DownloadTicketResponse;
import huynv.fileservice.service.FileLifecycleService;
import huynv.fileservice.service.MultipartUploadService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import org.springframework.security.core.Authentication;

/**
 * Verifies the REST endpoints that issue and redeem download tickets.
 */
class FileControllerDownloadTicketTest {

    private final FileLifecycleService fileLifecycleService = mock(FileLifecycleService.class);

    private final MultipartUploadService multipartUploadService = mock(MultipartUploadService.class);

    private final FileController fileController = new FileController(fileLifecycleService, multipartUploadService);

    /**
     * Verifies that the controller exposes the ticket-issuance endpoint.
     *
     * @return Performs assertions against the JSON response payload.
     */
    @Test
    void createDownloadTicketDelegatesToLifecycleService() {
        UUID fileId = UUID.randomUUID();
        Authentication authentication = mock(Authentication.class);
        when(fileLifecycleService.createDownloadTicket(any(), eq(fileId), anyBoolean()))
                .thenReturn(new DownloadTicketResponse(fileId, "ticket-123", Instant.parse("2026-05-01T12:00:00Z"), true));

        DownloadTicketResponse response = fileController.createDownloadTicket(authentication, fileId, new huynv.fileservice.dto.DownloadTicketIssueRequest(true));

        assertThat(response.fileId()).isEqualTo(fileId);
        assertThat(response.token()).isEqualTo("ticket-123");
        assertThat(response.singleUse()).isTrue();
    }

    /**
     * Verifies that the controller exposes the ticket-redemption download endpoint.
     *
     * @return Performs assertions against the streamed response body.
     */
    @Test
    void downloadByTicketDelegatesToLifecycleService() {
        Authentication authentication = mock(Authentication.class);
        when(fileLifecycleService.downloadByTicket(any(), eq("ticket-123")))
                .thenReturn(ResponseEntity.ok()
                        .contentType(MediaType.TEXT_PLAIN)
                        .body(new InputStreamResource(new ByteArrayInputStream("hello".getBytes()))));

        ResponseEntity<InputStreamResource> response = fileController.downloadByTicket(authentication, "ticket-123");

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_PLAIN);
    }
}


