package huynv.userservice.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Defines service-local configuration for Kafka publishing, caching, and paging behavior.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "user-service")
public class UserServiceProperties {

    @Min(1)
    @Max(500)
    private int maxPageSize = 100;

    @Valid
    @NotNull
    private Kafka kafka = new Kafka();

    @Valid
    @NotNull
    private Cache cache = new Cache();

    @Valid
    @NotNull
    private Outbox outbox = new Outbox();

    @Valid
    @NotNull
    private Idempotency idempotency = new Idempotency();

    @Valid
    @NotNull
    private Security security = new Security();

    /**
     * Defines Kafka topic settings owned by user-service.
     */
    @Getter
    @Setter
    public static class Kafka {

        @NotBlank
        private String eventsTopic = "user.events";

        @NotBlank
        private String dlqTopic = "user.events.dlq";
    }

    /**
     * Defines tenant-aware Redis cache TTL settings.
     */
    @Getter
    @Setter
    public static class Cache {

        @NotNull
        private Duration userLookupTtl = Duration.ofMinutes(10);

        @NotNull
        private Duration preferencesTtl = Duration.ofMinutes(5);
    }

    /**
     * Defines transactional outbox publisher behavior for user-service.
     */
    @Getter
    @Setter
    public static class Outbox {

        private boolean publisherEnabled = true;

        @Min(1)
        @Max(1000)
        private int batchSize = 100;

        @NotNull
        private Duration fixedDelay = Duration.ofSeconds(1);

        @NotNull
        private Duration sendTimeout = Duration.ofSeconds(10);

        @Min(1)
        @Max(20)
        private int maxAttempts = 5;

        @NotNull
        private Duration initialBackoff = Duration.ofMillis(250);

        @DecimalMin("1.0")
        private double backoffMultiplier = 2.0d;

        @NotNull
        private Duration maxBackoff = Duration.ofSeconds(5);

        @NotNull
        private Duration processingTimeout = Duration.ofMinutes(1);
    }

    /**
     * Defines REST API idempotency settings for write endpoints.
     */
    @Getter
    @Setter
    public static class Idempotency {

        private boolean enabled = true;

        @NotNull
        private Duration ttl = Duration.ofHours(24);

        @NotNull
        private Duration cleanupFixedDelay = Duration.ofHours(1);
    }

    /**
     * Defines security settings for internal service-to-service traffic.
     */
    @Getter
    @Setter
    public static class Security {

        @Valid
        @NotNull
        private Internal internal = new Internal();
    }

    /**
     * Defines the claims and roles accepted for trusted internal callers.
     */
    @Getter
    @Setter
    public static class Internal {

        @NotEmpty
        private List<String> allowedAuthorizedParties = new ArrayList<>(List.of(
                "gateway-service",
                "order-service",
                "payment-service",
                "inventory-service",
                "product-service",
                "notification-service",
                "audit-log-service",
                "file-service",
                "order-view-service",
                "product-view-service",
                "dlq-replayer-service"
        ));

        @NotEmpty
        private List<String> allowedAudiences = new ArrayList<>(List.of("user-service-internal", "user-service"));

        @NotEmpty
        private List<String> allowedScopes = new ArrayList<>(List.of("user-service.internal", "internal:user-service:read"));

        @NotEmpty
        private List<String> allowedServiceRoles = new ArrayList<>(List.of("service_user_reader", "service-user-reader"));
    }
}

