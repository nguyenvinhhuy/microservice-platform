package huynv.notificationservice.service;

import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Renders email and notification templates from classpath resources under templates/.
 */
@Service
public class TemplateRenderingService {

    private final TemplateEngine templateEngine;
    private final TemplatePathResolver templatePathResolver;

    /**
     * Creates a template rendering service backed by Thymeleaf.
     *
     * @param templateEngine Thymeleaf template engine used to render templates.
     * @param templatePathResolver Resolver used to enforce canonical template locations and tenant overrides.
     * @return Initializes a template rendering service.
     */
    public TemplateRenderingService(TemplateEngine templateEngine, TemplatePathResolver templatePathResolver) {
        this.templateEngine = Objects.requireNonNull(templateEngine, "templateEngine");
        this.templatePathResolver = Objects.requireNonNull(templatePathResolver, "templatePathResolver");
    }

    /**
     * Renders a template with the provided model values.
     *
     * @param templateName Template resource name under templates/ without any path traversal.
     * @param model Model values to bind into the template.
     * @return Returns the rendered template output string.
     */
    public String render(String templateName, Map<String, Object> model) {
        String resolvedName = templatePathResolver.resolveForTenant(null, templateName);
        Context context = new Context(Locale.US);
        if (model != null && !model.isEmpty()) {
            context.setVariables(model);
        }
        return templateEngine.process(resolvedName, context);
    }

    /**
     * Renders a template using tenant override resolution followed by default template fallback.
     *
     * @param tenantId Tenant identifier used to locate tenant override templates when present.
     * @param templateName Base template name or canonical path requested by the caller.
     * @param model Model values to bind into the template.
     * @return Returns the rendered template output string.
     */
    public String renderForTenant(Long tenantId, String templateName, Map<String, Object> model) {
        String resolvedName = templatePathResolver.resolveForTenant(tenantId, templateName);
        Context context = new Context(Locale.US);
        if (model != null && !model.isEmpty()) {
            context.setVariables(model);
        }
        return templateEngine.process(resolvedName, context);
    }
}
