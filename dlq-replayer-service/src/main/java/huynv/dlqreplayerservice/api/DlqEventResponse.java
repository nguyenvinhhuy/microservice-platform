package huynv.dlqreplayerservice.api;

import huynv.dlqreplayerservice.model.DlqEventStatus;

import java.time.OffsetDateTime;

/**
 * Defines the response model for DLQ event inspection APIs.
 */
public record DlqEventResponse(
        Long id,
        String topic,
        Integer partition,
        Long offset,
        String key,
        String originalTopic,
        DlqEventStatus status,
        OffsetDateTime createdAt
) {
}

