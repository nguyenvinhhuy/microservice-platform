package huynv.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.orderservice.domain.IdempotencyKey;
import huynv.orderservice.domain.IdempotencyStatus;
import huynv.orderservice.repository.IdempotencyKeyRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for IdempotencyService — covers begin/complete/fail/replay/bindOrder contract.
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private IdempotencyKeyRepository repository;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private IdempotencyService idempotencyService;

    /**
     * Verifies that the first call to begin with a previously unseen key saves a new PROCESSING
     * IdempotencyKey and returns a Decision indicating the key was newly created.
     *
     * @return Asserts decision.created() is true and decision.key() is the saved instance.
     */
    @Test
    void begin_creates_new_key_on_first_call() {
        when(repository.findByTenantIdAndRequestIdAndApiName(1L, "key-1", "CREATE_ORDER"))
                .thenReturn(Optional.empty());
        IdempotencyKey saved = key(1L, IdempotencyStatus.PROCESSING);
        when(repository.save(any())).thenReturn(saved);

        IdempotencyService.Decision decision = idempotencyService.begin(1L, "key-1", "CREATE_ORDER");

        assertThat(decision.created()).isTrue();
        assertThat(decision.key()).isSameAs(saved);
    }

    /**
     * Verifies that begin returns the existing key without calling save when the key is already
     * stored, ensuring repeat calls do not produce duplicate idempotency records.
     *
     * @return Asserts decision.created() is false, decision.key() is the existing instance, and save is never called.
     */
    @Test
    void begin_returns_existing_key_without_inserting() {
        IdempotencyKey existing = key(1L, IdempotencyStatus.COMPLETED);
        when(repository.findByTenantIdAndRequestIdAndApiName(1L, "key-1", "CREATE_ORDER"))
                .thenReturn(Optional.of(existing));

        IdempotencyService.Decision decision = idempotencyService.begin(1L, "key-1", "CREATE_ORDER");

        assertThat(decision.created()).isFalse();
        assertThat(decision.key()).isSameAs(existing);
        verify(repository, never()).save(any());
    }

    /**
     * Verifies that a concurrent-insert race condition is handled gracefully: when save throws
     * DataIntegrityViolationException, begin re-queries the repository and returns the row
     * that was inserted by the concurrent request.
     *
     * @return Asserts decision.created() is false and decision.key() is the concurrently inserted row.
     */
    @Test
    void begin_handles_concurrent_insert_via_re_query() {
        IdempotencyKey raced = key(2L, IdempotencyStatus.PROCESSING);
        when(repository.findByTenantIdAndRequestIdAndApiName(1L, "key-1", "CREATE_ORDER"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(raced));
        when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

        IdempotencyService.Decision decision = idempotencyService.begin(1L, "key-1", "CREATE_ORDER");

        assertThat(decision.created()).isFalse();
        assertThat(decision.key()).isSameAs(raced);
    }

    /**
     * Verifies that complete transitions an idempotency key to COMPLETED status and stores
     * the JSON-serialized response payload on the record for future replay.
     *
     * @return Asserts key status is COMPLETED and responsePayload equals the serialized JSON string.
     */
    @Test
    void complete_marks_key_COMPLETED_with_serialized_payload() throws JsonProcessingException {
        IdempotencyKey k = key(10L, IdempotencyStatus.PROCESSING);
        when(repository.findById(10L)).thenReturn(Optional.of(k));
        when(repository.save(k)).thenReturn(k);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"result\":\"ok\"}");

        idempotencyService.complete(10L, UUID.randomUUID(), "response");

        assertThat(k.getStatus()).isEqualTo(IdempotencyStatus.COMPLETED);
        assertThat(k.getResponsePayload()).isEqualTo("{\"result\":\"ok\"}");
    }

    /**
     * Verifies that fail transitions an idempotency key to FAILED status and stores
     * the JSON-serialized error payload on the record to capture the failure context.
     *
     * @return Asserts key status is FAILED and responsePayload equals the serialized JSON string.
     */
    @Test
    void fail_marks_key_FAILED_with_serialized_payload() throws JsonProcessingException {
        IdempotencyKey k = key(10L, IdempotencyStatus.PROCESSING);
        when(repository.findById(10L)).thenReturn(Optional.of(k));
        when(repository.save(k)).thenReturn(k);
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"status\":\"FAILED\"}");

        idempotencyService.fail(10L, UUID.randomUUID(), "error");

        assertThat(k.getStatus()).isEqualTo(IdempotencyStatus.FAILED);
        assertThat(k.getResponsePayload()).isEqualTo("{\"status\":\"FAILED\"}");
    }

    /**
     * Verifies that replay deserializes the stored JSON payload back to the requested type,
     * enabling callers to return the original response for duplicate requests.
     *
     * @return Asserts the deserialized value equals the expected "replayed" string.
     */
    @Test
    void replay_deserializes_stored_payload() throws JsonProcessingException {
        IdempotencyKey k = key(5L, IdempotencyStatus.COMPLETED);
        k.setResponsePayload("{\"orderId\":\"test\"}");
        when(objectMapper.readValue("{\"orderId\":\"test\"}", String.class)).thenReturn("replayed");

        String result = idempotencyService.replay(k, String.class);

        assertThat(result).isEqualTo("replayed");
    }

    /**
     * Verifies that replay returns null when no response payload has been stored on the key,
     * such as when the key is still in PROCESSING state and the response is not yet available.
     *
     * @return Asserts the result is null.
     */
    @Test
    void replay_returns_null_when_payload_absent() {
        IdempotencyKey k = key(5L, IdempotencyStatus.PROCESSING);
        k.setResponsePayload(null);

        String result = idempotencyService.replay(k, String.class);

        assertThat(result).isNull();
    }

    /**
     * Verifies that bindOrder sets the orderId on the key when it has not been bound yet,
     * linking the idempotency record to the newly created order for subsequent lookups.
     *
     * @return Asserts key.getOrderId() equals the supplied orderId and repository.save is called.
     */
    @Test
    void bindOrder_updates_orderId_when_previously_null() {
        UUID orderId = UUID.randomUUID();
        IdempotencyKey k = key(7L, IdempotencyStatus.PROCESSING);
        when(repository.findById(7L)).thenReturn(Optional.of(k));
        when(repository.save(k)).thenReturn(k);

        idempotencyService.bindOrder(7L, orderId);

        assertThat(k.getOrderId()).isEqualTo(orderId);
        verify(repository).save(k);
    }

    /**
     * Verifies that bindOrder does not overwrite an orderId that is already bound to the key,
     * preventing accidental re-association of an idempotency record to a different order.
     *
     * @return Asserts key.getOrderId() retains the original value and repository.save is never called.
     */
    @Test
    void bindOrder_skips_update_when_orderId_already_bound() {
        UUID existingId = UUID.randomUUID();
        IdempotencyKey k = key(7L, IdempotencyStatus.PROCESSING);
        k.setOrderId(existingId);
        when(repository.findById(7L)).thenReturn(Optional.of(k));

        idempotencyService.bindOrder(7L, UUID.randomUUID());

        assertThat(k.getOrderId()).isEqualTo(existingId);
        verify(repository, never()).save(any());
    }

    /**
     * Builds a minimal IdempotencyKey with the given ID and status for use in stub setup and assertions.
     *
     * @param id     The primary key to assign to the IdempotencyKey.
     * @param status The idempotency status (PROCESSING, COMPLETED, or FAILED) to assign.
     * @return A built IdempotencyKey instance scoped to tenant 1 with fixed requestId and apiName.
     */
    private IdempotencyKey key(Long id, IdempotencyStatus status) {
        return IdempotencyKey.builder()
                .id(id)
                .tenantId(1L)
                .requestId("key")
                .apiName("API")
                .status(status)
                .build();
    }
}
