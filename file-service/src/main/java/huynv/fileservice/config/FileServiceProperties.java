package huynv.fileservice.config;

import huynv.fileservice.storage.StorageBucketStrategy;
import jakarta.validation.Valid;
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
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Holds tenant-aware runtime configuration for file-service security, storage, Kafka, caching, and idempotency behavior.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "file-service")
public class FileServiceProperties {

    @Valid
    private final Storage storage = new Storage();

    @Valid
    private final Kafka kafka = new Kafka();

    @Valid
    private final Cache cache = new Cache();

    @Valid
    private final Security security = new Security();

    @Valid
    private final Quota quota = new Quota();

    @Valid
    private final Idempotency idempotency = new Idempotency();

    @Valid
    private final Outbox outbox = new Outbox();

    @Valid
    private final Scan scan = new Scan();

    @Valid
    private final RateLimit rateLimit = new RateLimit();

    @Valid
    private final Multipart multipart = new Multipart();

    /**
     * Defines storage validation, bucket routing, and pre-signed URL behavior.
     */
    @Getter
    @Setter
    public static class Storage {

        @NotBlank
        private String endpoint = "http://localhost:9000";

        @NotBlank
        private String region = "us-east-1";

        @NotBlank
        private String accessKey = "minioadmin";

        @NotBlank
        private String secretKey = "minioadmin";

        @NotBlank
        private String publicBaseUrl = "http://localhost:9000";

        @NotBlank
        private String sharedBucket = "platform-files";

        @NotNull
        private StorageBucketStrategy bucketStrategy = StorageBucketStrategy.SHARED;

        @NotBlank
        private String bucketPrefix = "tenant-files";

        @NotNull
        private Duration presignedUploadTtl = Duration.ofMinutes(15);

        @NotNull
        private Duration presignedDownloadTtl = Duration.ofMinutes(15);

        @NotNull
        private Duration downloadTicketTtl = Duration.ofMinutes(5);

        @Min(1)
        private long maxUploadSizeBytes = 25L * 1024L * 1024L;

        @NotBlank
        private String encryptionMode = "NONE";

        private String encryptionKeyReference;

        @NotBlank
        private String quarantineBucket = "platform-files-quarantine";

        @NotEmpty
        private Set<String> allowedExtensions = new LinkedHashSet<>(Set.of("pdf", "jpg", "jpeg", "png", "gif", "webp", "txt"));

        @NotEmpty
        private Set<String> allowedMimeTypes = new LinkedHashSet<>(Set.of(
                "application/pdf",
                "image/jpeg",
                "image/png",
                "image/gif",
                "image/webp",
                "text/plain"
        ));
    }

    /**
     * Defines outbound and inbound Kafka topic configuration for file events.
     */
    @Getter
    @Setter
    public static class Kafka {

        @NotBlank
        private String eventsTopic = "file.events";

        @NotBlank
        private String dlqTopic = "file.events.dlq";

        @NotBlank
        private String scanResultsTopic = "file.scan.results";

        @NotBlank
        private String scanConsumerGroupId = "file-service-scan-results";

        private boolean scanConsumerEnabled = true;
    }

    /**
     * Defines Redis-backed cache TTL settings.
     */
    @Getter
    @Setter
    public static class Cache {

        @NotNull
        private Duration metadataTtl = Duration.ofMinutes(10);

        @NotNull
        private Duration quotaTtl = Duration.ofMinutes(5);

        @NotNull
        private Duration presignStateTtl = Duration.ofMinutes(20);
    }

    /**
     * Defines JWT and trusted internal-service authorization settings.
     */
    @Getter
    @Setter
    public static class Security {

        @Valid
        private final Internal internal = new Internal();

        /**
         * Defines machine-to-machine authorization requirements for trusted internal callers.
         */
        @Getter
        @Setter
        public static class Internal {

            @NotEmpty
            private Set<String> allowedAuthorizedParties = new LinkedHashSet<>(Set.of("gateway-service", "notification-service"));

            @NotEmpty
            private Set<String> allowedAudiences = new LinkedHashSet<>(Set.of("file-service-internal"));

            @NotEmpty
            private Set<String> allowedScopes = new LinkedHashSet<>(Set.of("file-service.internal"));

            @NotEmpty
            private Set<String> allowedServiceRoles = new LinkedHashSet<>(Set.of("ROLE_SERVICE", "ROLE_ADMIN"));
        }
    }

    /**
     * Defines tenant quota defaults and cleanup windows.
     */
    @Getter
    @Setter
    public static class Quota {

        @Min(1)
        private long defaultQuotaBytes = 250L * 1024L * 1024L;

        @NotNull
        private Duration pendingUploadTtl = Duration.ofHours(1);
    }

    /**
     * Defines API idempotency retention and enablement settings.
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
     * Defines outbox retry and publisher loop settings.
     */
    @Getter
    @Setter
    public static class Outbox {

        @Min(1)
        @Max(20)
        private int maxAttempts = 5;

        @Min(1)
        private int batchSize = 200;

        @NotNull
        private Duration fixedDelay = Duration.ofSeconds(1);

        @NotNull
        private Duration sendTimeout = Duration.ofSeconds(10);

        @NotNull
        private Duration processingTimeout = Duration.ofMinutes(1);
    }

    /**
     * Defines malware-scan worker behavior and ClamAV connectivity.
     */
    @Getter
    @Setter
    public static class Scan {

        private boolean workerEnabled = true;

        @NotBlank
        private String workerConsumerGroupId = "file-service-scan-worker";

        @NotBlank
        private String mode = "CLAMAV";

        @NotBlank
        private String host = "localhost";

        @Min(1)
        private int port = 3310;

        @NotNull
        private Duration timeout = Duration.ofSeconds(15);

        @Min(0)
        private int maxRetries = 3;

        @NotNull
        private Duration retryDelay = Duration.ofMinutes(5);
    }

    /**
     * Defines Redis-backed abuse-protection limits.
     */
    @Getter
    @Setter
    public static class RateLimit {

        private boolean enabled = true;

        @Min(1)
        private int uploadRequestsPerMinute = 30;

        @Min(1)
        private int downloadRequestsPerMinute = 120;

        @Min(1)
        private int presignRequestsPerMinute = 60;

        @Min(1)
        private int ipBurstPerMinute = 240;
    }

    /**
     * Defines multipart upload limits, session TTLs, and cleanup settings.
     */
    @Getter
    @Setter
    public static class Multipart {

        private boolean enabled = true;

        @NotNull
        private Duration sessionTtl = Duration.ofHours(6);

        @NotNull
        private Duration presignedPartTtl = Duration.ofMinutes(20);

        @NotNull
        private Duration cleanupFixedDelay = Duration.ofMinutes(30);

        @Min(1)
        private int maxPartCount = 10_000;
    }
}

