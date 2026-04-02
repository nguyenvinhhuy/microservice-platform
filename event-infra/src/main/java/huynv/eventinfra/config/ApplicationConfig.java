package huynv.eventinfra.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Configures core application beans for notification-service.
 */
@Configuration
@EnableConfigurationProperties({
        NotificationProperties.class,
        ProviderProperties.class,
        NotificationRateLimitOverridesProperties.class
})
public class ApplicationConfig {

    /**
     * Provides an ObjectMapper bean for JSON serialization and deserialization across the service.
     *
     * @return Returns an ObjectMapper configured for backward-compatible parsing and Java time support.
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        return mapper;
    }
}

