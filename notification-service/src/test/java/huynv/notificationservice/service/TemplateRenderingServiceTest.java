package huynv.notificationservice.service;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that Thymeleaf templates are rendered correctly for outbound notifications.
 */
@SpringBootTest
@ActiveProfiles("test")
public class TemplateRenderingServiceTest {

    @Autowired
    private TemplateRenderingService templateRenderingService;

    /**
     * Renders the order confirmation template and asserts expected fields are interpolated.
     *
     * @return Performs assertions against the rendered template output.
     */
    @Test
    public void shouldRenderOrderConfirmationTemplate() {
        String html = templateRenderingService.render("order-confirmation", Map.of("orderId", "ORDER-123"));
        assertThat(html).contains("ORDER-123");
    }
}

