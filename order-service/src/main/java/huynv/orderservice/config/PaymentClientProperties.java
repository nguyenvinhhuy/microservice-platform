package huynv.orderservice.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Provides configuration for calling payment-service internal APIs.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "clients.payment")
public class PaymentClientProperties {

    @NotBlank
    private String baseUrl;

    @NotBlank
    private String processPath;
}

