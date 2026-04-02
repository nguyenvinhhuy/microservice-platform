package huynv.eventinfra.config;

import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Objects;

/**
 * Configures ShedLock so scheduled publishers run on only one instance at a time.
 */
@Configuration
public class ShedLockConfig {

    /**
     * Creates a lock provider backed by the primary service database.
     *
     * @param dataSource Service datasource used for lock persistence.
     * @return Returns a lock provider using database time to avoid clock skew across instances.
     */
    @Bean
    public LockProvider lockProvider(DataSource dataSource) {
        Objects.requireNonNull(dataSource, "dataSource");
        return new JdbcTemplateLockProvider(
                JdbcTemplateLockProvider.Configuration.builder()
                        .withJdbcTemplate(new JdbcTemplate(dataSource))
                        .build()
        );
    }
}

