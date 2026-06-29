package huynv.productservice.config;

import huynv.productservice.context.UserContext;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorProvider")
public class JpaAuditingConfiguration {

    private static final String SYSTEM_AUDITOR = "system";

    /**
     * Creates the auditor provider used by Spring Data JPA auditing.
     *
     * @return Returns the current user identifier when request context exists, or the system auditor value otherwise.
     */
    @Bean
    public AuditorAware<String> auditorProvider() {
        return () -> {
            UserContext userContext = UserContext.getCurrentUserContext();
            if (userContext != null && userContext.getUserId() != null) {
                return Optional.of(userContext.getUserId().toString());
            }
            return Optional.of(SYSTEM_AUDITOR);
        };
    }
}
