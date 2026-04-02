package huynv.dlqreplayerservice.service;

import huynv.dlqreplayerservice.model.DlqEvent;
import huynv.dlqreplayerservice.model.DlqEventStatus;
import huynv.dlqreplayerservice.repository.DlqEventRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

/**
 * Replays stored DLQ events back to their original topics using Kafka publishing.
 */
@Service
public class DlqReplayService {

    private final DlqEventRepository dlqEventRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    /**
     * Creates a replay service that publishes stored DLQ records back to Kafka.
     *
     * @param dlqEventRepository Repository used to load and update DLQ events.
     * @param kafkaTemplate Kafka template used to republish stored payloads.
     * @return Initializes a DLQ replay service instance.
     */
    public DlqReplayService(DlqEventRepository dlqEventRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.dlqEventRepository = Objects.requireNonNull(dlqEventRepository, "dlqEventRepository");
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate");
    }

    /**
     * Replays the given stored DLQ event back to its original topic.
     *
     * @param id Stored DLQ event identifier.
     * @param overrideTopic Optional topic override used when original topic is missing.
     * @return Performs side effects by publishing the stored record and marking it as REPLAYED.
     */
    @Transactional
    public void replay(Long id, String overrideTopic) {
        Objects.requireNonNull(id, "id");
        DlqEvent event = dlqEventRepository.findById(id).orElseThrow();
        String target = overrideTopic != null && !overrideTopic.isBlank() ? overrideTopic : event.getOriginalTopic();
        if (target == null || target.isBlank()) {
            throw new IllegalStateException("Missing originalTopic for dlqEventId=" + id + ".");
        }
        kafkaTemplate.send(target, event.getKey(), event.getPayload());
        event.setStatus(DlqEventStatus.REPLAYED);
        dlqEventRepository.save(event);
    }

    /**
     * Skips the given stored DLQ event without replay.
     *
     * @param id Stored DLQ event identifier.
     * @return Performs a side effect by marking the event as SKIPPED.
     */
    @Transactional
    public void skip(Long id) {
        Objects.requireNonNull(id, "id");
        DlqEvent event = dlqEventRepository.findById(id).orElseThrow();
        event.setStatus(DlqEventStatus.SKIPPED);
        dlqEventRepository.save(event);
    }
}

