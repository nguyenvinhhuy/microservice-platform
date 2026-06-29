package huynv.fileservice.config;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;

import java.util.Objects;

/**
 * Reports MinIO readiness by performing a lightweight S3-compatible API call.
 */
@Component("minio")
public class MinioHealthIndicator implements HealthIndicator {

    private final S3Client s3Client;

    /**
     * Creates a MinIO health indicator backed by the S3-compatible client.
     *
     * @param s3Client S3-compatible client used for readiness checks.
     * @return Initializes the MinIO health indicator.
     */
    public MinioHealthIndicator(S3Client s3Client) {
        this.s3Client = Objects.requireNonNull(s3Client, "s3Client");
    }

    /**
     * Performs a lightweight readiness check against the MinIO-compatible storage API.
     *
     * @return Returns the resulting health status.
     */
    @Override
    public Health health() {
        try {
            int bucketCount = s3Client.listBuckets().buckets().size();
            return Health.up().withDetail("bucketCount", bucketCount).build();
        } catch (Exception ex) {
            return Health.down(ex).build();
        }
    }
}

