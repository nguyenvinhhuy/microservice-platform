package huynv.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;

/**
 * Defines a unified JSON envelope for all Kafka events published across the platform.
 *
 * @param <T> Data payload type stored under the data field.
 * @return Returns an immutable JSON-compatible event envelope record.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties(ignoreUnknown = true)
public record BaseEvent<T>(
        @JsonProperty("eventId") String eventId,
        @JsonProperty("eventType") String eventType,
        @JsonProperty("source") String source,
        @JsonProperty("eventTime") Instant eventTime,
        @JsonProperty("aggregateId") String aggregateId,
        @JsonProperty("aggregateVersion") long aggregateVersion,
        @JsonProperty("dataSchema") String dataSchema,
        @JsonProperty("traceId") String traceId,
        @JsonProperty("correlationId") String correlationId,
        @JsonProperty("causationId") String causationId,
        @JsonProperty("data") T data
) {
}

