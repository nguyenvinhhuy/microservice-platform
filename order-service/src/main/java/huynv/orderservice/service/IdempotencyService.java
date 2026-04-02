package huynv.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.orderservice.domain.IdempotencyKey;
import huynv.orderservice.domain.IdempotencyStatus;
import huynv.orderservice.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository repository;
    private final ObjectMapper objectMapper;

    public record Decision(IdempotencyKey key, boolean created) {
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    /**
     * begin operation.
     *
     * @param tenantId input parameter
     * @param requestId input parameter
     * @param apiName input parameter
     * @return begin result
     */
    public Decision begin(Long tenantId, String requestId, String apiName) {
        IdempotencyKey existing = repository.findByTenantIdAndRequestIdAndApiName(tenantId, requestId, apiName).orElse(null);
        if (existing != null) {
            return new Decision(existing, false);
        }

        IdempotencyKey created = IdempotencyKey.builder()
                .tenantId(tenantId)
                .requestId(requestId)
                .apiName(apiName)
                .status(IdempotencyStatus.PROCESSING)
                .build();
        try {
            return new Decision(repository.save(created), true);
        } catch (DataIntegrityViolationException ex) {
            IdempotencyKey raced = repository.findByTenantIdAndRequestIdAndApiName(tenantId, requestId, apiName)
                    .orElseThrow(() -> ex);
            return new Decision(raced, false);
        }
    }

    /**
     * complete operation.
     *
     * @param keyId input parameter
     * @param orderId input parameter
     * @param response input parameter
     * @return performs side effects defined by this operation
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void complete(Long keyId, UUID orderId, Object response) {
        IdempotencyKey key = repository.findById(keyId).orElseThrow();
        key.setOrderId(orderId);
        key.setStatus(IdempotencyStatus.COMPLETED);
        key.setResponsePayload(toJson(response));
        repository.save(key);
    }

    /**
     * fail operation.
     *
     * @param keyId input parameter
     * @param orderId input parameter
     * @param response input parameter
     * @return performs side effects defined by this operation
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void fail(Long keyId, UUID orderId, Object response) {
        IdempotencyKey key = repository.findById(keyId).orElseThrow();
        key.setOrderId(orderId);
        key.setStatus(IdempotencyStatus.FAILED);
        key.setResponsePayload(toJson(response));
        repository.save(key);
    }

    /**
     * Associates persisted idempotency record with order reference before command finishes.
     *
     * @param keyId idempotency row identifier created at command start.
     * @param orderId business order id used for deterministic in-flight responses.
     * @return no return; updates idempotency row to include stable order reference.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void bindOrder(Long keyId, UUID orderId) {
        IdempotencyKey key = repository.findById(keyId).orElseThrow();
        if (key.getOrderId() == null) {
            key.setOrderId(orderId);
            repository.save(key);
        }
    }

    /**
     * replay operation.
     *
     * @param key input parameter
     * @param targetType input parameter
     * @return replay result
     */
    public <T> T replay(IdempotencyKey key, Class<T> targetType) {
        if (key.getResponsePayload() == null) {
            return null;
        }
        try {
            return objectMapper.readValue(key.getResponsePayload(), targetType);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to deserialize idempotent payload", ex);
        }
    }

    /**
     * toJson operation.
     *
     * @param response input parameter
     * @return toJson result
     */
    private String toJson(Object response) {
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize idempotent payload", ex);
        }
    }
}
