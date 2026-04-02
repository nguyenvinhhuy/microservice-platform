package huynv.eventinfra.config;

import io.micrometer.tracing.Tracer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides a fallback tracing configuration to keep the service operational when tracing is not enabled.
 */
@Configuration
public class TracingConfig {

    /**
     * Provides a no-op tracer when Micrometer tracing is not configured by auto-configuration.
     *
     * @return Returns a no-op tracer implementation.
     */
    @Bean
    @ConditionalOnMissingBean(Tracer.class)
    public Tracer tracer() {
        return Tracer.NOOP;
    }
}
