package huynv.productservice.config;

import huynv.productservice.context.UserContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfiguration {

    @Bean
    /**
     * auditorProvider operation.
     *
     * @return auditorProvider result
     */
    public AuditorAware<String> auditorProvider() {
        return () -> {
            UserContext userContext = UserContext.getCurrentUserContext();
            if (userContext != null && userContext.getUserId() != null) {
                return Optional.of(userContext.getUserId().toString());
            }
            return Optional.of("system"); // Fallback if no user context
        };
    }
}
