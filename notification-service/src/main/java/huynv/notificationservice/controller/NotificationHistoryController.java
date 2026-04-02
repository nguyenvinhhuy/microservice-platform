package huynv.notificationservice.controller;

import huynv.notificationservice.dto.NotificationHistoryResponse;
import huynv.notificationservice.repository.NotificationHistoryRepository;
import huynv.notificationservice.security.JwtTenantUserExtractor;
import huynv.notificationservice.security.TenantUserContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * Exposes a tenant-isolated API for retrieving notification history records.
 */
@RestController
@RequestMapping("/notifications")
public class NotificationHistoryController {

    private final NotificationHistoryRepository repository;
    private final JwtTenantUserExtractor tenantUserExtractor;

    /**
     * Creates a controller for listing notification history records for the authenticated tenant.
     *
     * @param repository Notification history repository used for data access.
     * @param tenantUserExtractor Extractor used to read tenant and user identifiers from JWT claims.
     * @return Initializes a notification history controller.
     */
    public NotificationHistoryController(NotificationHistoryRepository repository, JwtTenantUserExtractor tenantUserExtractor) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.tenantUserExtractor = Objects.requireNonNull(tenantUserExtractor, "tenantUserExtractor");
    }

    /**
     * Lists recent notifications for the authenticated tenant and user.
     *
     * @param authentication Authenticated principal containing validated JWT claims.
     * @param limit Maximum number of records to return for bounded queries.
     * @return Returns a list of recent notification history responses for the caller.
     */
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<NotificationHistoryResponse> list(
            Authentication authentication,
            @RequestParam(name = "limit", defaultValue = "50") int limit
    ) {
        TenantUserContext context = tenantUserExtractor.extract(authentication);
        if (context.tenantId() == null || context.userId() == null) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(200, limit));
        return repository.findByTenantIdAndUserIdOrderByCreatedAtDesc(
                        context.tenantId(),
                        context.userId(),
                        PageRequest.of(0, safeLimit)
                )
                .stream()
                .map(NotificationHistoryResponse::from)
                .toList();
    }
}
