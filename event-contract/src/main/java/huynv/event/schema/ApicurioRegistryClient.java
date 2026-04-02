package huynv.event.schema;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Registers JSON Schemas in Apicurio Registry using the REST API.
 */
public final class ApicurioRegistryClient implements SchemaRegistryClient {

    private final HttpClient httpClient;
    private final URI registryBaseUrl;
    private final String groupId;
    private final Duration timeout;

    /**
     * Creates an Apicurio registry client instance using the provided base URL.
     *
     * @param registryBaseUrl Apicurio Registry base URL.
     * @param groupId Registry group id used to namespace artifacts.
     * @param timeout Network timeout for registration calls.
     * @return Initializes an Apicurio registry client.
     */
    public ApicurioRegistryClient(URI registryBaseUrl, String groupId, Duration timeout) {
        this(HttpClient.newHttpClient(), registryBaseUrl, groupId, timeout);
    }

    /**
     * Creates an Apicurio registry client instance with an explicit HttpClient.
     *
     * @param httpClient HTTP client used to call the registry.
     * @param registryBaseUrl Apicurio Registry base URL.
     * @param groupId Registry group id used to namespace artifacts.
     * @param timeout Network timeout for registration calls.
     * @return Initializes an Apicurio registry client.
     */
    public ApicurioRegistryClient(HttpClient httpClient, URI registryBaseUrl, String groupId, Duration timeout) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        this.registryBaseUrl = Objects.requireNonNull(registryBaseUrl, "registryBaseUrl");
        this.groupId = groupId == null || groupId.isBlank() ? "default" : groupId;
        this.timeout = timeout == null ? Duration.ofSeconds(3) : timeout;
    }

    @Override
    public void register(String schemaId, String jsonSchema) {
        Objects.requireNonNull(schemaId, "schemaId");
        Objects.requireNonNull(jsonSchema, "jsonSchema");

        URI putUrl = registryBaseUrl.resolve("/apis/registry/v2/groups/" + groupId + "/artifacts/" + schemaId);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(putUrl)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(jsonSchema, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status == 404) {
                create(schemaId, jsonSchema);
                return;
            }
            if (status < 200 || status >= 300) {
                throw new IllegalStateException("Schema registry registration failed schemaId=" + schemaId + " status=" + status + ".");
            }
        } catch (IOException | InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Schema registry registration failed schemaId=" + schemaId + ".", ex);
        }
    }

    private void create(String schemaId, String jsonSchema) throws IOException, InterruptedException {
        URI postUrl = registryBaseUrl.resolve("/apis/registry/v2/groups/" + groupId + "/artifacts");
        HttpRequest request = HttpRequest.newBuilder()
                .uri(postUrl)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("X-Registry-ArtifactId", schemaId)
                .POST(HttpRequest.BodyPublishers.ofString(jsonSchema, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        int status = response.statusCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("Schema registry create failed schemaId=" + schemaId + " status=" + status + ".");
        }
    }
}


