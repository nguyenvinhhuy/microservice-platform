package huynv.fileservice.controller;

import huynv.fileservice.dto.FileMetadataResponse;
import huynv.fileservice.service.FileLifecycleService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Exposes trusted internal file-service APIs for service-to-service callers.
 */
@RestController
@RequestMapping("/internal/files")
public class InternalFileController {

    private final FileLifecycleService fileLifecycleService;

    /**
     * Creates an internal controller that delegates queries to the lifecycle service.
     *
     * @param fileLifecycleService Lifecycle service implementing internal file queries.
     * @return Initializes the internal file controller.
     */
    public InternalFileController(FileLifecycleService fileLifecycleService) {
        this.fileLifecycleService = fileLifecycleService;
    }

    /**
     * Returns file metadata for a trusted internal caller using the tenant from the validated JWT.
     *
     * @param authentication Current trusted internal principal.
     * @param fileId File identifier.
     * @return Returns the file metadata response.
     */
    @GetMapping("/{fileId}")
    public FileMetadataResponse getMetadata(Authentication authentication, @PathVariable UUID fileId) {
        return fileLifecycleService.getMetadata(authentication, fileId);
    }
}

