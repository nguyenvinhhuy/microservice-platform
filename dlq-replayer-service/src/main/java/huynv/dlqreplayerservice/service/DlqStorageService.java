package huynv.dlqreplayerservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.event.idempotency.IdempotencyService;
import huynv.dlqreplayerservice.model.DlqEvent;
import huynv.dlqreplayerservice.repository.DlqEventRepository;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Persists DLQ records for later inspection and replay.
 */
@Service
public class DlqStorageService {

    private final DlqEventRepository dlqEventRepository;
    private final IdempotencyService idempotencyService;
    private final ObjectMapper objectMapper;

    /**
     * Creates a DLQ storage service that persists consumed DLQ records.
     *
     * @param dlqEventRepository Repository used to persist stored DLQ events.
     * @param idempotencyService Idempotency service used to skip duplicate deliveries.
     * @param objectMapper ObjectMapper used to serialize headers to JSON.
     * @return Initializes a DLQ storage service instance.
     */
    public DlqStorageService(DlqEventRepository dlqEventRepository, IdempotencyService idempotencyService, ObjectMapper objectMapper) {
        this.dlqEventRepository = Objects.requireNonNull(dlqEventRepository, "dlqEventRepository");
        this.idempotencyService = Objects.requireNonNull(idempotencyService, "idempotencyService");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    /**
     * Stores a DLQ record exactly once using processed_events for consumer idempotency and a unique constraint on offsets.
     *
     * @param record Kafka record received from a DLQ topic.
     * @return Performs a side effect by persisting the DLQ record if it has not been stored before.
     */
    @Transactional
    public void store(ConsumerRecord<String, String> record) {
        Objects.requireNonNull(record, "record");
        String dedupeKey = record.topic() + ":" + record.partition() + ":" + record.offset();
        if (idempotencyService.alreadyProcessed(dedupeKey)) {
            return;
        }

        DlqEvent event = new DlqEvent();
        event.setTopic(record.topic());
        event.setPartition(record.partition());
        event.setOffset(record.offset());
        event.setKey(record.key());
        event.setPayload(record.value() == null ? "" : record.value());
        event.setHeadersJson(toHeadersJson(record));
        event.setOriginalTopic(extractOriginalTopic(record));

        try {
            dlqEventRepository.save(event);
        } catch (DataIntegrityViolationException ignored) {
            // Another consumer stored the same topic/partition/offset; treat as processed.
        }
        idempotencyService.markProcessed(dedupeKey);
    }

    private String toHeadersJson(ConsumerRecord<String, String> record) {
        Map<String, String> headers = new LinkedHashMap<>();
        if (record.headers() != null) {
            for (Header header : record.headers()) {
                if (header != null && header.key() != null) {
                    headers.put(header.key(), header.value() == null ? "" : new String(header.value(), StandardCharsets.UTF_8));
                }
            }
        }
        try {
            return objectMapper.writeValueAsString(headers);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private static String extractOriginalTopic(ConsumerRecord<String, String> record) {
        if (record.headers() == null) {
            return null;
        }
        Header header = record.headers().lastHeader("kafka_dlt-original-topic");
        if (header == null || header.value() == null) {
            header = record.headers().lastHeader("dlt-original-topic");
        }
        if (header == null || header.value() == null) {
            return null;
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}


