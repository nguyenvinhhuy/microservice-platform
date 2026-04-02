package huynv.orderservice.config;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Data
@Validated
@ConfigurationProperties(prefix = "clients.inventory")
public class InventoryClientProperties {

    @NotBlank
    private String baseUrl;

    @NotBlank
    private String reservePath;

    @NotBlank
    private String confirmPath;

    @NotBlank
    private String releasePath;
}
