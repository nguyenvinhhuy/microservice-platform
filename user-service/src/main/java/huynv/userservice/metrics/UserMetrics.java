package huynv.userservice.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Objects;

/**
 * Records the user-service metrics required for request handling, lookups, updates, and event publishing.
 */
@Component
public class UserMetrics {

    private final MeterRegistry meterRegistry;

    /**
     * Creates a metrics recorder backed by the active Micrometer meter registry.
     *
     * @param meterRegistry Meter registry used to create counters and timers.
     * @return Initializes a user metrics recorder instance.
     */
    public UserMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    }

    /**
     * Records a request counter increment and request latency sample for a specific route.
     *
     * @param route HTTP route path used for tagging.
     * @param status HTTP status code returned to the caller.
     * @param latency Request latency duration.
     * @return Performs side effects by recording request metrics.
     */
    public void recordRequest(String route, int status, Duration latency) {
        meterRegistry.counter("user_requests_total", "route", sanitize(route), "status", String.valueOf(status)).increment();
        meterRegistry.timer("user_request_latency_seconds", "route", sanitize(route)).record(Objects.requireNonNull(latency, "latency"));
    }

    /**
     * Records a profile update counter increment.
     *
     * @return Performs a side effect by incrementing the profile update counter.
     */
    public void recordProfileUpdate() {
        meterRegistry.counter("user_profile_updates_total").increment();
    }

    /**
     * Records a user lookup latency sample.
     *
     * @param latency Lookup latency duration.
     * @return Performs a side effect by recording the lookup timer sample.
     */
    public void recordLookup(Duration latency) {
        meterRegistry.timer("user_lookup_latency_seconds").record(Objects.requireNonNull(latency, "latency"));
    }

    /**
     * Records a published event counter increment tagged by event type.
     *
     * @param eventType Event type name that was persisted to the outbox.
     * @return Performs a side effect by incrementing the event publication counter.
     */
    public void recordEventPublished(String eventType) {
        meterRegistry.counter("user_events_published_total", "eventType", sanitize(eventType)).increment();
        meterRegistry.counter("user_event_publish_total", "eventType", sanitize(eventType)).increment();
    }

    /**
     * Records a failed event publication attempt before the outbox row is persisted.
     *
     * @param eventType Event type name that failed validation or serialization.
     * @return Performs a side effect by incrementing the failed publish counter.
     */
    public void recordEventPublishFailed(String eventType) {
        meterRegistry.counter("user_event_publish_failed_total", "eventType", sanitize(eventType)).increment();
    }

    /**
     * Records a cache hit for a named cache region.
     *
     * @param cacheName Cache region name.
     * @return Performs a side effect by incrementing the cache-hit counter.
     */
    public void recordCacheHit(String cacheName) {
        meterRegistry.counter("user_cache_hit_total", "cache", sanitize(cacheName)).increment();
    }

    /**
     * Records a cache miss for a named cache region.
     *
     * @param cacheName Cache region name.
     * @return Performs a side effect by incrementing the cache-miss counter.
     */
    public void recordCacheMiss(String cacheName) {
        meterRegistry.counter("user_cache_miss_total", "cache", sanitize(cacheName)).increment();
    }

    /**
     * Records an API idempotency cache hit.
     *
     * @param operation Logical operation name resolved for the idempotent request.
     * @return Performs a side effect by incrementing the idempotency-hit counter.
     */
    public void recordIdempotencyHit(String operation) {
        meterRegistry.counter("api_idempotency_hit_total", "operation", sanitize(operation)).increment();
    }

    /**
     * Records an API idempotency conflict.
     *
     * @param operation Logical operation name resolved for the idempotent request.
     * @return Performs a side effect by incrementing the idempotency-conflict counter.
     */
    public void recordIdempotencyConflict(String operation) {
        meterRegistry.counter("api_idempotency_conflict_total", "operation", sanitize(operation)).increment();
    }

    /**
     * Replaces null or blank tag values with a deterministic fallback.
     *
     * @param value Candidate tag value.
     * @return Returns the original value when present, or the string unknown when blank.
     */
    private String sanitize(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }
}

