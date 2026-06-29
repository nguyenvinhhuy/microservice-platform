package huynv.fileservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.fileservice.config.FileServiceProperties;
import huynv.fileservice.domain.ApiIdempotencyRecord;
import huynv.fileservice.domain.ApiIdempotencyStatus;
import huynv.fileservice.exception.ConflictException;
import huynv.fileservice.repository.ApiIdempotencyRepository;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Implements PostgreSQL-backed REST idempotency for multi-instance-safe command endpoints.
 */
@Service
public class ApiIdempotencyService {

    private final ApiIdempotencyRepository apiIdempotencyRepository;
    private final ObjectMapper objectMapper;
    private final FileServiceProperties properties;
    private final TransactionTemplate requiresNewTransactionTemplate;

    /**
     * Creates an API idempotency service backed by the api_idempotency table.
     *
     * @param apiIdempotencyRepository Repository used to load and persist idempotency rows.
     * @param objectMapper ObjectMapper used to serialize request and response payloads deterministically.
     * @param properties File-service properties containing TTL and cleanup settings.
     * @param transactionManager Transaction manager used for cleanup jobs.
     * @return Initializes the API idempotency service.
     */
    public ApiIdempotencyService(
            ApiIdempotencyRepository apiIdempotencyRepository,
            ObjectMapper objectMapper,
            FileServiceProperties properties,
            PlatformTransactionManager transactionManager
    ) {
        this.apiIdempotencyRepository = Objects.requireNonNull(apiIdempotencyRepository, "apiIdempotencyRepository");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.properties = Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(transactionManager, "transactionManager");
        this.requiresNewTransactionTemplate = new TransactionTemplate(transactionManager);
    }

    /**
     * Executes a protected command once for a tenant and request path while reusing completed responses on retries.
     *
     * @param tenantId Tenant identifier.
     * @param requestPath Stable request path under protection.
     * @param idempotencyKey Client-provided idempotency key.
     * @param requestBody Request body used to detect semantic mismatches.
     * @param responseType Response type used to deserialize cached successful responses.
     * @param operation Operation to execute when no completed record exists.
     * @param <T> Response payload type.
     * @return Returns the fresh or cached response payload.
     */
    @Transactional
    public <T> T execute(
            UUID tenantId,
            String requestPath,
            String idempotencyKey,
            Object requestBody,
            Class<T> responseType,
            Supplier<T> operation
    ) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(requestPath, "requestPath");
        Objects.requireNonNull(responseType, "responseType");
        Objects.requireNonNull(operation, "operation");
        if (!properties.getIdempotency().isEnabled()) {
            return operation.get();
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ConflictException("IDEMPOTENCY_KEY_REQUIRED", "An Idempotency-Key header is required for this command.");
        }
        String requestHash = hashRequest(requestBody);
        ApiIdempotencyRecord existing = apiIdempotencyRepository
                .findByTenantIdAndRequestPathAndIdempotencyKey(tenantId, requestPath, idempotencyKey)
                .orElse(null);
        if (existing != null) {
            return handleExisting(existing, requestHash, responseType);
        }

        ApiIdempotencyRecord record = new ApiIdempotencyRecord(
                tenantId,
                idempotencyKey,
                requestPath,
                requestHash,
                Instant.now().plus(properties.getIdempotency().getTtl())
        );
        try {
            apiIdempotencyRepository.saveAndFlush(record);
        } catch (DataIntegrityViolationException ex) {
            ApiIdempotencyRecord concurrent = apiIdempotencyRepository
                    .findByTenantIdAndRequestPathAndIdempotencyKey(tenantId, requestPath, idempotencyKey)
                    .orElseThrow(() -> ex);
            return handleExisting(concurrent, requestHash, responseType);
        }

        try {
            T result = operation.get();
            record.markCompleted(serialize(result));
            apiIdempotencyRepository.save(record);
            return result;
        } catch (RuntimeException ex) {
            record.markFailed(serializeFailure(ex));
            apiIdempotencyRepository.save(record);
            throw ex;
        }
    }

    /**
     * Deletes expired idempotency rows on a fixed schedule.
     *
     * @return Performs a side effect by purging expired idempotency rows.
     */
    @Scheduled(fixedDelayString = "${file-service.idempotency.cleanup-fixed-delay:PT1H}")
    @SchedulerLock(name = "file-service-api-idempotency-cleanup", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void cleanupExpired() {
        if (!properties.getIdempotency().isEnabled()) {
            return;
        }
        requiresNewTransactionTemplate.executeWithoutResult(status -> apiIdempotencyRepository.deleteExpired(Instant.now()));
    }

    /**
     * Handles an existing idempotency row and returns the cached response when appropriate.
     *
     * @param record Existing idempotency record.
     * @param requestHash Deterministic hash of the current request body.
     * @param responseType Response type used to deserialize cached responses.
     * @param <T> Response payload type.
     * @return Returns the cached response when the record is completed.
     */
    private <T> T handleExisting(ApiIdempotencyRecord record, String requestHash, Class<T> responseType) {
        if (!Objects.equals(record.getRequestHash(), requestHash)) {
            throw new ConflictException("IDEMPOTENCY_KEY_REUSED", "The Idempotency-Key was already used for a different request payload.");
        }
        if (record.getStatus() == ApiIdempotencyStatus.COMPLETED) {
            return deserialize(record.getResponseBody(), responseType);
        }
        if (record.getStatus() == ApiIdempotencyStatus.FAILED) {
            throw new ConflictException("IDEMPOTENT_REQUEST_FAILED", "A previous request with the same Idempotency-Key failed.");
        }
        throw new ConflictException("IDEMPOTENT_REQUEST_IN_PROGRESS", "A request with the same Idempotency-Key is still processing.");
    }

    /**
     * Serializes a successful response body for idempotent reuse.
     *
     * @param value Response payload to serialize.
     * @return Returns the serialized response body.
     */
    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize an idempotent response body.", ex);
        }
    }

    /**
     * Serializes a failure summary for operational diagnostics.
     *
     * @param ex Runtime exception raised by the protected command.
     * @return Returns a serialized failure summary.
     */
    private String serializeFailure(RuntimeException ex) {
        try {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "error", ex.getClass().getSimpleName(),
                    "message", ex.getMessage()
            ));
        } catch (Exception serializationException) {
            return "{\"error\":\"" + ex.getClass().getSimpleName() + "\"}";
        }
    }

    /**
     * Deserializes a cached successful response body.
     *
     * @param payload Serialized response body.
     * @param responseType Response type to deserialize.
     * @param <T> Response payload type.
     * @return Returns the deserialized response payload.
     */
    private <T> T deserialize(String payload, Class<T> responseType) {
        try {
            return objectMapper.readValue(payload, responseType);
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to deserialize a cached idempotent response body.", ex);
        }
    }

    /**
     * Hashes the request body deterministically for semantic mismatch detection.
     *
     * @param requestBody Request body to hash.
     * @return Returns the SHA-256 hash of the serialized request body.
     */
    private String hashRequest(Object requestBody) {
        try {
            String serialized = requestBody == null ? "null" : objectMapper.writeValueAsString(requestBody);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(serialized.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to hash the idempotent request payload.", ex);
        }
    }
}

