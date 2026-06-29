package huynv.fileservice.service;

import huynv.fileservice.domain.FileRecord;
import huynv.fileservice.domain.FileVisibility;
import huynv.fileservice.metrics.FileMetrics;
import huynv.fileservice.security.AuthenticatedUser;
import huynv.fileservice.security.JwtUserContextExtractor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;

import java.util.Objects;

/**
 * Centralizes file authorization rules so controllers stay thin and consistent.
 */
@Service
public class FileAuthorizationService {

    private final JwtUserContextExtractor jwtUserContextExtractor;
    private final FileMetrics fileMetrics;

    /**
     * Creates a centralized file authorization service.
     *
     * @param jwtUserContextExtractor JWT context extractor used to resolve tenant and user identity.
     * @param fileMetrics Metrics service used to track denied access attempts.
     */
    public FileAuthorizationService(JwtUserContextExtractor jwtUserContextExtractor, FileMetrics fileMetrics) {
        this.jwtUserContextExtractor = Objects.requireNonNull(jwtUserContextExtractor, "jwtUserContextExtractor");
        this.fileMetrics = Objects.requireNonNull(fileMetrics, "fileMetrics");
    }

    /**
     * Requires a validated authenticated user context for write operations.
     *
     * @param authentication Current Spring Security authentication.
     * @return Returns the extracted authenticated user context.
     */
    public AuthenticatedUser requireAuthenticated(Authentication authentication) {
        return jwtUserContextExtractor.extract(authentication);
    }

    /**
     * Enforces the file read authorization rules for the supplied file record.
     *
     * @param fileRecord File metadata record.
     * @param authentication Current Spring Security authentication, which may be null for public reads.
     */
    public void assertCanRead(FileRecord fileRecord, Authentication authentication) {
        Objects.requireNonNull(fileRecord, "fileRecord");
        if (fileRecord.getVisibility() == FileVisibility.PUBLIC) {
            if (authentication == null) {
                return;
            }
            AuthenticatedUser user = jwtUserContextExtractor.extract(authentication);
            if (!fileRecord.getTenantId().equals(user.tenantId()) && !user.isPrivileged()) {
                deny();
            }
            return;
        }
        AuthenticatedUser user = jwtUserContextExtractor.extract(authentication);
        if (!fileRecord.getTenantId().equals(user.tenantId())) {
            deny();
        }
        if (fileRecord.getVisibility() == FileVisibility.TENANT_SHARED) {
            return;
        }
        if (fileRecord.getOwnerUserId().equals(user.userId()) || user.isPrivileged()) {
            return;
        }
        deny();
    }

    /**
     * Enforces the file mutation authorization rules for delete and confirm operations.
     *
     * @param fileRecord File metadata record.
     * @param user Previously extracted authenticated user context.
     */
    public void assertCanMutate(FileRecord fileRecord, AuthenticatedUser user) {
        Objects.requireNonNull(fileRecord, "fileRecord");
        Objects.requireNonNull(user, "user");
        if (!fileRecord.getTenantId().equals(user.tenantId())) {
            deny();
        }
        if (!fileRecord.getOwnerUserId().equals(user.userId()) && !user.isPrivileged()) {
            deny();
        }
    }

    /**
     * Throws an access denied exception while updating denied-access metrics.
     */
    private void deny() {
        fileMetrics.recordAccessDenied();
        throw new AccessDeniedException("The current principal is not allowed to access the requested file.");
    }
}

