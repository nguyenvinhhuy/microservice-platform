package huynv.userservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.userservice.config.UserServiceProperties;
import huynv.userservice.domain.ApiIdempotencyEntity;
import huynv.userservice.domain.ApiIdempotencyState;
import huynv.userservice.exception.BadRequestException;
import huynv.userservice.exception.ConflictException;
import huynv.userservice.metrics.UserMetrics;
import huynv.userservice.repository.ApiIdempotencyRepository;
import huynv.userservice.security.AuthenticatedUser;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Coordinates persisted idempotency for REST write operations across retries and concurrent callers.
 */
@Service
public class ApiIdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(ApiIdempotencyService.class);

    private final ApiIdempotencyRepository apiIdempotencyRepository;
    private final ObjectMapper objectMapper;
    private final UserServiceProperties userServiceProperties;
    private final UserMetrics userMetrics;
    private final TransactionTemplate requiresNewTransactionTemplate;

    /**
     * Creates an API idempotency service backed by PostgreSQL state and separate state-transition transactions.
     *
     * @param apiIdempotencyRepository Repository used to persist idempotency rows.
     * @param objectMapper Object mapper used to serialize request and response payloads.
     * @param userServiceProperties User-service properties supplying TTL and scheduler settings.
     * @param userMetrics Metrics recorder used to track idempotency hits and conflicts.
     * @param platformTransactionManager Transaction manager used to create REQUIRES_NEW state transitions.
     * @return Initializes an API idempotency service instance.
     */
    public ApiIdempotencyService(
            ApiIdempotencyRepository apiIdempotencyRepository,
            ObjectMapper objectMapper,
            UserServiceProperties userServiceProperties,
            UserMetrics userMetrics,
            PlatformTransactionManager platformTransactionManager
    ) {
        this.apiIdempotencyRepository = Objects.requireNonNull(apiIdempotencyRepository, "apiIdempotencyRepository");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.userServiceProperties = Objects.requireNonNull(userServiceProperties, "userServiceProperties");
        this.userMetrics = Objects.requireNonNull(userMetrics, "userMetrics");
        Objects.requireNonNull(platformTransactionManager, "platformTransactionManager");
        this.requiresNewTransactionTemplate = new TransactionTemplate(platformTransactionManager);
        this.requiresNewTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Executes a write operation with persisted idempotency semantics for a tenant-scoped user request.
     *
     * @param authenticatedUser Tenant-scoped authenticated user context.
     * @param operation Logical operation name representing the endpoint contract.
     * @param idempotencyKey Client-provided idempotency key.
     * @param requestBody Request body used to derive the request hash.
     * @param responseType Response type used when replaying a cached response.
     * @param responseStatus HTTP status code returned by a successful execution.
     * @param action Business action to execute when no cached response is available.
     * @param <T> Response body type returned by the operation.
     * @return Returns either the newly produced response or the cached response from a prior successful attempt.
     */
    public <T> T execute(
            AuthenticatedUser authenticatedUser,
            String operation,
            String idempotencyKey,
            Object requestBody,
            Class<T> responseType,
            int responseStatus,
            Supplier<T> action
    ) {
        Objects.requireNonNull(authenticatedUser, "authenticatedUser");
        Objects.requireNonNull(responseType, "responseType");
        Objects.requireNonNull(action, "action");
        String normalizedOperation = requireNonBlank(operation, "operation");
        String normalizedIdempotencyKey = normalizeKey(idempotencyKey);
        String requestHash = requestHash(normalizedOperation, requestBody);
        ClaimDecision claimDecision = claim(authenticatedUser, normalizedOperation, normalizedIdempotencyKey, requestHash, responseType);
        if (claimDecision.cachedResponse() != null) {
            userMetrics.recordIdempotencyHit(normalizedOperation);
            return responseType.cast(claimDecision.cachedResponse());
        }
        if (claimDecision.conflictMessage() != null) {
            userMetrics.recordIdempotencyConflict(normalizedOperation);
            throw new ConflictException("IDEMPOTENCY_CONFLICT", claimDecision.conflictMessage());
        }
        try {
            T response = action.get();
            markCompleted(claimDecision.entityId(), responseStatus, response);
            return response;
        } catch (RuntimeException exception) {
            try {
                markFailed(claimDecision.entityId());
            } catch (RuntimeException markFailedEx) {
                log.error("Failed to mark idempotency row as failed, row may be stuck in PROCESSING id={}", claimDecision.entityId(), markFailedEx);
            }
            throw exception;
        }
    }

    /**
     * Deletes expired idempotency rows on a bounded schedule.
     *
     * @return Performs a side effect by purging stale idempotency rows from PostgreSQL.
     */
    @Scheduled(fixedDelayString = "${user-service.idempotency.cleanup-fixed-delay:1h}")
    @SchedulerLock(name = "user-service-api-idempotency-cleanup", lockAtMostFor = "PT5M", lockAtLeastFor = "PT30S")
    public void cleanupExpired() {
        if (!userServiceProperties.getIdempotency().isEnabled()) {
            return;
        }
        requiresNewTransactionTemplate.executeWithoutResult(status -> apiIdempotencyRepository.deleteExpired(Instant.now()));
    }

    /**
     * Claims or resolves idempotency state in a dedicated transaction.
     *
     * @param authenticatedUser Tenant-scoped authenticated user context.
     * @param operation Logical operation name representing the endpoint contract.
     * @param idempotencyKey Client-provided idempotency key.
     * @param requestHash Stable request hash for the incoming request.
     * @param responseType Response type used to deserialize cached bodies.
     * @param <T> Response body type returned by the operation.
     * @return Returns a claim decision that describes whether work should proceed or be replayed.
     */
    private <T> ClaimDecision claim(
            AuthenticatedUser authenticatedUser,
            String operation,
            String idempotencyKey,
            String requestHash,
            Class<T> responseType
    ) {
        return requiresNewTransactionTemplate.execute(status -> {
            Instant now = Instant.now();
            Instant expiresAt = now.plus(userServiceProperties.getIdempotency().getTtl());
            ApiIdempotencyEntity existing = apiIdempotencyRepository
                    .findByTenantIdAndUserIdAndOperationAndIdempotencyKey(
                            authenticatedUser.tenantId(),
                            authenticatedUser.userId(),
                            operation,
                            idempotencyKey
                    )
                    .orElse(null);
            if (existing != null) {
                return resolveExisting(existing, requestHash, expiresAt, responseType);
            }
            ApiIdempotencyEntity created = new ApiIdempotencyEntity(
                    UUID.randomUUID(),
                    authenticatedUser.tenantId(),
                    authenticatedUser.userId(),
                    operation,
                    idempotencyKey,
                    requestHash,
                    expiresAt
            );
            try {
                apiIdempotencyRepository.saveAndFlush(created);
                return ClaimDecision.started(created.getId());
            } catch (DataIntegrityViolationException exception) {
                ApiIdempotencyEntity duplicated = apiIdempotencyRepository
                        .findByTenantIdAndUserIdAndOperationAndIdempotencyKey(
                                authenticatedUser.tenantId(),
                                authenticatedUser.userId(),
                                operation,
                                idempotencyKey
                        )
                        .orElseThrow(() -> exception);
                return resolveExisting(duplicated, requestHash, expiresAt, responseType);
            }
        });
    }

    /**
     * Resolves an existing persisted idempotency row for a retry or duplicate request.
     *
     * @param entity Existing idempotency entity.
     * @param requestHash Stable request hash for the incoming request.
     * @param expiresAt Expiration time to apply when restarting a failed or expired request.
     * @param responseType Response type used to deserialize cached responses.
     * @param <T> Response body type returned by the operation.
     * @return Returns a claim decision describing the next action.
     */
    private <T> ClaimDecision resolveExisting(ApiIdempotencyEntity entity, String requestHash, Instant expiresAt, Class<T> responseType) {
        if (!Objects.equals(entity.getRequestHash(), requestHash)) {
            return ClaimDecision.conflict("The provided Idempotency-Key was already used with a different request payload.");
        }
        if (entity.getExpiresAt().isBefore(Instant.now()) || entity.getState() == ApiIdempotencyState.FAILED) {
            entity.restart(requestHash, expiresAt);
            apiIdempotencyRepository.saveAndFlush(entity);
            return ClaimDecision.started(entity.getId());
        }
        if (entity.getState() == ApiIdempotencyState.PROCESSING) {
            return ClaimDecision.conflict("The request is already being processed for the provided Idempotency-Key.");
        }
        if (entity.getResponseBody() == null || entity.getResponseBody().isBlank()) {
            return ClaimDecision.conflict("The stored idempotent response is unavailable for replay.");
        }
        return ClaimDecision.cached(entity.getId(), deserialize(entity.getResponseBody(), responseType));
    }

    /**
     * Marks a claimed request as completed in a dedicated transaction.
     *
     * @param entityId Idempotency row identifier.
     * @param responseStatus HTTP status code returned to the client.
     * @param response Response body returned to the client.
     * @return Performs a side effect by storing the cached response body.
     */
    private void markCompleted(UUID entityId, int responseStatus, Object response) {
        requiresNewTransactionTemplate.executeWithoutResult(status -> {
            ApiIdempotencyEntity entity = apiIdempotencyRepository.findById(entityId)
                    .orElseThrow(() -> new IllegalStateException("Idempotency row not found after successful execution."));
            entity.markCompleted(responseStatus, serialize(response));
            apiIdempotencyRepository.save(entity);
        });
    }

    /**
     * Marks a claimed request as failed in a dedicated transaction.
     *
     * @param entityId Idempotency row identifier.
     * @return Performs a side effect by storing the failed state for deterministic retries.
     */
    private void markFailed(UUID entityId) {
        requiresNewTransactionTemplate.executeWithoutResult(status -> apiIdempotencyRepository.findById(entityId).ifPresent(entity -> {
            entity.markFailed();
            apiIdempotencyRepository.save(entity);
        }));
    }

    /**
     * Serializes a request or response object to JSON.
     *
     * @param value Value to serialize.
     * @return Returns the serialized JSON string.
     */
    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize idempotency payload.", exception);
        }
    }

    /**
     * Deserializes a cached response body back into the controller return type.
     *
     * @param json Serialized JSON response body.
     * @param responseType Response type to deserialize.
     * @param <T> Response body type returned by the operation.
     * @return Returns the deserialized cached response body.
     */
    private <T> T deserialize(String json, Class<T> responseType) {
        try {
            return objectMapper.readValue(json, responseType);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize cached idempotent response.", exception);
        }
    }

    /**
     * Computes a stable request hash for the logical operation and payload.
     *
     * @param operation Logical operation name representing the endpoint contract.
     * @param requestBody Request body used to derive the hash.
     * @return Returns a Base64URL-encoded SHA-256 request hash.
     */
    private String requestHash(String operation, Object requestBody) {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            messageDigest.update(operation.getBytes(StandardCharsets.UTF_8));
            if (requestBody != null) {
                messageDigest.update(serialize(requestBody).getBytes(StandardCharsets.UTF_8));
            }
            return Base64.getUrlEncoder().withoutPadding().encodeToString(messageDigest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to compute API idempotency request hash.", exception);
        }
    }

    /**
     * Normalizes and validates a client-provided idempotency key.
     *
     * @param idempotencyKey Client-provided idempotency key header.
     * @return Returns a trimmed idempotency key value.
     */
    private String normalizeKey(String idempotencyKey) {
        String normalized = requireNonBlank(idempotencyKey, "Idempotency-Key").replace("\r", "").replace("\n", "").trim();
        if (normalized.length() > 200) {
            throw new BadRequestException("IDEMPOTENCY_KEY_INVALID", "The Idempotency-Key header must not exceed 200 characters.");
        }
        return normalized;
    }

    /**
     * Verifies that a required string value is present and non-blank.
     *
     * @param value Candidate string value.
     * @param fieldName Logical field name used in error messages.
     * @return Returns the validated string value.
     */
    private String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BadRequestException("IDEMPOTENCY_KEY_REQUIRED", "The " + fieldName + " header is required for this write endpoint.");
        }
        return value;
    }

    /**
     * Represents the persisted state claim result for one logical request.
     *
     * @param entityId Idempotency row identifier.
     * @param cachedResponse Cached response body when the request was already completed.
     * @param conflictMessage Conflict message when the request cannot proceed.
     * @return Returns an immutable claim decision.
     */
    private record ClaimDecision(UUID entityId, Object cachedResponse, String conflictMessage) {

        /**
         * Creates a decision indicating that new work should begin for the claimed row.
         *
         * @param entityId Claimed idempotency row identifier.
         * @return Returns a claim decision that starts new work.
         */
        private static ClaimDecision started(UUID entityId) {
            return new ClaimDecision(entityId, null, null);
        }

        /**
         * Creates a decision that replays a cached response.
         *
         * @param entityId Persisted idempotency row identifier.
         * @param cachedResponse Cached response body to replay.
         * @return Returns a claim decision that reuses a prior response.
         */
        private static ClaimDecision cached(UUID entityId, Object cachedResponse) {
            return new ClaimDecision(entityId, cachedResponse, null);
        }

        /**
         * Creates a decision indicating that the request conflicts with existing persisted state.
         *
         * @param conflictMessage Human-readable conflict message.
         * @return Returns a conflict claim decision.
         */
        private static ClaimDecision conflict(String conflictMessage) {
            return new ClaimDecision(null, null, conflictMessage);
        }
    }
}

