package huynv.inventoryservice.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Declares Jackson components required by outbox serialization and Kafka publishing.
 */
@Configuration
public class JacksonConfig {

    /**
     * Creates an ObjectMapper configured with Java time support for event envelope serialization.
     *
     * @return Returns a configured ObjectMapper bean for the inventory-service application context.
     */
    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        return mapper;
    }
}

