package huynv.fileservice.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.util.Objects;

/**
 * Creates S3-compatible clients configured to talk to MinIO using path-style access and static credentials.
 */
@Configuration
public class StorageConfig {

    /**
     * Creates the primary S3 client used for object uploads, downloads, and metadata checks.
     *
     * @param properties File-service properties containing MinIO endpoint and credentials.
     * @return Returns an S3Client configured for MinIO compatibility.
     */
    @Bean
    public S3Client s3Client(FileServiceProperties properties) {
        Objects.requireNonNull(properties, "properties");
        return S3Client.builder()
                .endpointOverride(URI.create(properties.getStorage().getEndpoint()))
                .region(Region.of(properties.getStorage().getRegion()))
                .credentialsProvider(credentialsProvider(properties))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .httpClientBuilder(UrlConnectionHttpClient.builder())
                .build();
    }

    /**
     * Creates an S3 presigner used to generate pre-signed upload and download URLs.
     *
     * @param properties File-service properties containing MinIO endpoint and credentials.
     * @return Returns an S3Presigner configured for MinIO compatibility.
     */
    @Bean
    public S3Presigner s3Presigner(FileServiceProperties properties) {
        Objects.requireNonNull(properties, "properties");
        return S3Presigner.builder()
                .endpointOverride(URI.create(properties.getStorage().getPublicBaseUrl()))
                .region(Region.of(properties.getStorage().getRegion()))
                .credentialsProvider(credentialsProvider(properties))
                .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
                .build();
    }

    /**
     * Creates a static credentials provider for the MinIO-compatible object storage endpoint.
     *
     * @param properties File-service properties containing access key and secret key values.
     * @return Returns a static credentials provider for the configured storage account.
     */
    private StaticCredentialsProvider credentialsProvider(FileServiceProperties properties) {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(
                properties.getStorage().getAccessKey(),
                properties.getStorage().getSecretKey()
        );
        return StaticCredentialsProvider.create(credentials);
    }
}

