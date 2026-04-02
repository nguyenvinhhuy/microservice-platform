package huynv.orderservice.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Provides configuration for calling product-service internal APIs.
 */
@Data
@Validated
@ConfigurationProperties(prefix = "clients.product")
public class ProductClientProperties {

    @NotBlank
    private String baseUrl;

    @NotBlank
    private String getByIdPath;
}

