package huynv.notificationservice.service;

import huynv.eventinfra.config.NotificationProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Resolves tenant-aware template names while enforcing canonical template locations.
 */
@Component
public class TemplatePathResolver {

    private static final Logger log = LoggerFactory.getLogger(TemplatePathResolver.class);

    private final NotificationProperties properties;
    private final ResourceLoader resourceLoader;

    /**
     * Creates a template resolver that checks classpath resources for tenant overrides before falling back to defaults.
     *
     * @param properties Notification properties containing the default template version identifier.
     * @param resourceLoader Resource loader used to check for template existence in the classpath.
     * @return Initializes a tenant-aware template path resolver.
     */
    public TemplatePathResolver(NotificationProperties properties, ResourceLoader resourceLoader) {
        this.properties = Objects.requireNonNull(properties, "properties");
        this.resourceLoader = Objects.requireNonNull(resourceLoader, "resourceLoader");
    }

    /**
     * Resolves a requested template name into a canonical classpath template location.
     *
     * @param tenantId Tenant identifier used to select tenant override templates when available.
     * @param requestedTemplateName Template name requested by the caller, either a base name or a canonical path.
     * @return Returns the resolved Thymeleaf template name without prefix or suffix.
     */
    public String resolveForTenant(Long tenantId, String requestedTemplateName) {
        String normalized = safeTemplateName(requestedTemplateName);
        if (normalized.contains("/")) {
            validateCanonicalLocation(normalized);
            return normalized;
        }
        return resolveBaseTemplateName(tenantId, normalized);
    }

    /**
     * Resolves a base template name into a canonical default or tenant override template path.
     *
     * @param tenantId Tenant identifier used to probe tenant override templates.
     * @param baseTemplateName Base template name without any path segments.
     * @return Returns the resolved template name under templates/default/** or templates/tenant-<tenantId>/**.
     */
    private String resolveBaseTemplateName(Long tenantId, String baseTemplateName) {
        String version = safeVersion(properties.getTemplates().getDefaultVersion());
        String defaultFolder = canonicalDefaultFolder(properties.getTemplates().getDefaultTenantFolder());

        if (tenantId != null) {
            String tenantCandidate = "tenant-" + tenantId + "/" + baseTemplateName + "/" + version;
            if (templateExists(tenantCandidate)) {
                return tenantCandidate;
            }
        }
        return defaultFolder + "/" + baseTemplateName + "/" + version;
    }

    /**
     * Checks whether a Thymeleaf template exists on the classpath using Spring's resource loading semantics.
     *
     * @param templateName Resolved template name without prefix or suffix.
     * @return Returns true when the corresponding classpath resource exists.
     */
    private boolean templateExists(String templateName) {
        Resource resource = resourceLoader.getResource("classpath:/templates/" + templateName + ".html");
        return resource.exists();
    }

    /**
     * Validates that a resolved template name is constrained to canonical template root folders.
     *
     * @param resolvedTemplateName Resolved template name including folder segments.
     * @return Performs a side effect by throwing an exception when the template location is not canonical.
     */
    private static void validateCanonicalLocation(String resolvedTemplateName) {
        if (resolvedTemplateName.startsWith("default/")) {
            return;
        }
        if (resolvedTemplateName.startsWith("tenant-")) {
            return;
        }
        throw new IllegalArgumentException("templateName must resolve under templates/default/ or templates/tenant-<tenantId>/.");
    }

    /**
     * Normalizes the default template folder name to the canonical folder `default`.
     *
     * @param configuredFolder Folder name configured via properties.
     * @return Returns the canonical default folder name that will be used for template resolution.
     */
    private String canonicalDefaultFolder(String configuredFolder) {
        if (configuredFolder == null || configuredFolder.isBlank()) {
            return "default";
        }
        String trimmed = configuredFolder.trim().replace('\\', '/');
        if (!"default".equals(trimmed)) {
            log.warn("Ignoring non-canonical template folder configuration folder={} enforcedFolder=default", trimmed);
            return "default";
        }
        return trimmed;
    }

    /**
     * Normalizes the default template version value used in `default/<template>/<version>`.
     *
     * @param configuredVersion Version value configured via properties.
     * @return Returns a safe version value that does not contain path traversal characters.
     */
    private static String safeVersion(String configuredVersion) {
        if (configuredVersion == null || configuredVersion.isBlank()) {
            return "v1";
        }
        String trimmed = configuredVersion.trim().replace('\\', '/');
        if (trimmed.contains("/") || trimmed.contains("..")) {
            throw new IllegalArgumentException("defaultVersion must not contain path separators.");
        }
        return trimmed;
    }

    /**
     * Validates a template name to prevent path traversal and absolute template resolution.
     *
     * @param templateName Template name requested by the caller.
     * @return Returns a normalized, safe template name for further resolution.
     */
    private static String safeTemplateName(String templateName) {
        if (templateName == null || templateName.isBlank()) {
            throw new IllegalArgumentException("templateName must be provided.");
        }
        String normalized = templateName.replace('\\', '/').trim();
        if (normalized.contains("..") || normalized.startsWith("/")) {
            throw new IllegalArgumentException("templateName must not contain path traversal.");
        }
        return normalized;
    }
}

