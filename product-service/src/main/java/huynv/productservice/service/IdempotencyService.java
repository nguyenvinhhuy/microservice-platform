package huynv.productservice.service;

import huynv.productservice.model.IdempotencyKey;
import huynv.productservice.repository.IdempotencyKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class IdempotencyService {

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    @Transactional(readOnly = true)
    /**
     * getKey operation.
     *
     * @param key input parameter
     * @return getKey result
     */
    public Optional<IdempotencyKey> getKey(UUID key) {
        return idempotencyKeyRepository.findById(key);
    }

    @Transactional
    /**
     * saveKey operation.
     *
     * @param key input parameter
     * @param status input parameter
     * @param body input parameter
     * @return performs side effects defined by this operation
     */
    public void saveKey(UUID key, int status, String body) {
        IdempotencyKey idempotencyKey = IdempotencyKey.builder()
                .idempotencyKey(key)
                .responseStatus(status)
                .responseBody(body)
                .createdAt(OffsetDateTime.now())
                .build();
        idempotencyKeyRepository.save(idempotencyKey);
    }
}
