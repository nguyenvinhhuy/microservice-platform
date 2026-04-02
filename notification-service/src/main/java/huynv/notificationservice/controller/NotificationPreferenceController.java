package huynv.notificationservice.controller;

import huynv.notificationservice.dto.NotificationPreferenceResponse;
import huynv.notificationservice.dto.UpdateNotificationPreferencesRequest;
import huynv.notificationservice.security.JwtTenantUserExtractor;
import huynv.notificationservice.security.TenantUserContext;
import huynv.notificationservice.service.NotificationPreferenceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * Exposes a tenant-isolated API for managing notification channel preferences for the authenticated user.
 */
@RestController
@RequestMapping("/notification-preferences")
public class NotificationPreferenceController {

    private final NotificationPreferenceService preferenceService;
    private final JwtTenantUserExtractor tenantUserExtractor;

    /**
     * Creates a controller for reading and updating notification preferences for the authenticated user.
     *
     * @param preferenceService Service used to read and persist preference updates.
     * @param tenantUserExtractor Extractor used to read tenant and user identifiers from JWT claims.
     * @return Initializes a notification preference controller.
     */
    public NotificationPreferenceController(NotificationPreferenceService preferenceService, JwtTenantUserExtractor tenantUserExtractor) {
        this.preferenceService = Objects.requireNonNull(preferenceService, "preferenceService");
        this.tenantUserExtractor = Objects.requireNonNull(tenantUserExtractor, "tenantUserExtractor");
    }

    /**
     * Lists stored channel preferences for the authenticated tenant and user.
     *
     * @param authentication Authenticated principal containing validated JWT claims.
     * @return Returns the stored channel preferences for the caller.
     */
    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<NotificationPreferenceResponse> list(Authentication authentication) {
        TenantUserContext context = tenantUserExtractor.extract(authentication);
        if (context.tenantId() == null || context.userId() == null) {
            return List.of();
        }
        return preferenceService.list(context.tenantId(), context.userId())
                .stream()
                .map(NotificationPreferenceResponse::from)
                .toList();
    }

    /**
     * Applies channel preference updates for the authenticated tenant and user.
     *
     * @param authentication Authenticated principal containing validated JWT claims.
     * @param request Request payload containing channel updates to apply.
     * @return Returns the updated preference records.
     */
    @PutMapping
    @ResponseStatus(HttpStatus.OK)
    public List<NotificationPreferenceResponse> put(Authentication authentication, @Valid @RequestBody UpdateNotificationPreferencesRequest request) {
        Objects.requireNonNull(request, "request");

        TenantUserContext context = tenantUserExtractor.extract(authentication);
        if (context.tenantId() == null || context.userId() == null) {
            return List.of();
        }

        for (UpdateNotificationPreferencesRequest.PreferenceUpdate update : request.preferences()) {
            preferenceService.upsert(context.tenantId(), context.userId(), update.channel(), update.enabled());
        }

        return preferenceService.list(context.tenantId(), context.userId())
                .stream()
                .map(NotificationPreferenceResponse::from)
                .toList();
    }
}

