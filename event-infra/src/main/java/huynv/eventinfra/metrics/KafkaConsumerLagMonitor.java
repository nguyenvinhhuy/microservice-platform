package huynv.eventinfra.metrics;

import huynv.eventinfra.config.NotificationProperties;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Counter;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Periodically computes consumer lag for key notification-service consumer groups and exports it as a gauge.
 */
@Component
public class KafkaConsumerLagMonitor {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerLagMonitor.class);

    private final AdminClient adminClient;
    private final NotificationProperties properties;
    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, AtomicLong> lagByGroupAndTopic = new ConcurrentHashMap<>();
    private final Counter lagThresholdBreached;
    private final long warnThreshold;

    /**
     * Creates a lag monitor that queries the Kafka cluster using the Admin API.
     *
     * @param bootstrapServers Kafka bootstrap servers used to connect the Admin client.
     * @param properties Notification properties used to enumerate relevant consumer groups and topics.
     * @param meterRegistry Meter registry used to register lag gauges.
     * @return Initializes a Kafka consumer lag monitor.
     */
    public KafkaConsumerLagMonitor(@Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
                                   @Value("${notification.kafka.lag-monitor.warn-threshold:10000}") long warnThreshold,
                                   NotificationProperties properties,
                                   MeterRegistry meterRegistry) {
        Objects.requireNonNull(bootstrapServers, "bootstrapServers");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
        this.adminClient = AdminClient.create(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers));
        this.warnThreshold = Math.max(0L, warnThreshold);
        this.lagThresholdBreached = Counter.builder("kafka_consumer_lag_threshold_breached_total")
                .description("Total number of times kafka_consumer_lag exceeded the configured warning threshold.")
                .register(this.meterRegistry);
    }

    /**
     * Closes the AdminClient used for lag queries when the application context shuts down.
     *
     * @return Performs a side effect by releasing Kafka Admin client resources.
     */
    @PreDestroy
    public void close() {
        try {
            adminClient.close(Duration.ofSeconds(2));
        } catch (Exception ignored) {
        }
    }

    /**
     * Samples consumer lag for configured groups and topics and updates Micrometer gauges.
     *
     * @return Performs side effects by querying Kafka offsets and updating lag gauges.
     */
    @Scheduled(fixedDelayString = "${notification.kafka.lag-monitor.fixed-delay-ms:10000}")
    public void sampleLag() {
        List<GroupTopics> monitored = monitoredGroups();
        for (GroupTopics groupTopics : monitored) {
            for (String topic : groupTopics.topics) {
                try {
                    long lag = computeLag(groupTopics.groupId, topic, Duration.ofSeconds(3));
                    lagGauge(groupTopics.groupId, topic).set(lag);
                    if (warnThreshold > 0 && lag > warnThreshold) {
                        lagThresholdBreached.increment();
                        log.warn("Consumer lag above threshold consumerGroup={} topic={} lag={} threshold={} action=scale_or_throttle",
                                groupTopics.groupId,
                                topic,
                                lag,
                                warnThreshold);
                    }
                } catch (Exception ex) {
                    log.debug("Lag sampling failed groupId={} topic={} message={}", groupTopics.groupId, topic, ex.getMessage());
                }
            }
        }
    }

    private long computeLag(String groupId, String topic, Duration timeout) throws Exception {
        ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(groupId);
        Map<TopicPartition, OffsetAndMetadata> committed = offsetsResult.partitionsToOffsetAndMetadata().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        Map<TopicPartition, OffsetSpec> requests = new HashMap<>();
        for (TopicPartition tp : committed.keySet()) {
            if (!topic.equals(tp.topic())) {
                continue;
            }
            requests.put(tp, OffsetSpec.latest());
        }
        if (requests.isEmpty()) {
            return 0L;
        }

        ListOffsetsResult endOffsets = adminClient.listOffsets(requests);
        Map<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> latest = endOffsets.all().get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        long lag = 0L;
        for (Map.Entry<TopicPartition, ListOffsetsResult.ListOffsetsResultInfo> entry : latest.entrySet()) {
            TopicPartition tp = entry.getKey();
            long end = entry.getValue().offset();
            OffsetAndMetadata committedOffset = committed.get(tp);
            long committedValue = committedOffset == null ? end : committedOffset.offset();
            long partitionLag = Math.max(0L, end - committedValue);
            lag += partitionLag;
        }
        return lag;
    }

    private AtomicLong lagGauge(String groupId, String topic) {
        String key = groupId + "|" + topic;
        return lagByGroupAndTopic.computeIfAbsent(key, ignored -> {
            AtomicLong holder = new AtomicLong(0L);
            Gauge.builder("kafka_consumer_lag", holder, AtomicLong::get)
                    .description("Kafka consumer lag computed from end offsets minus committed offsets.")
                    .tag("consumer_group", groupId)
                    .tag("topic", topic)
                    .register(meterRegistry);
            return holder;
        });
    }

    private List<GroupTopics> monitoredGroups() {
        List<GroupTopics> groups = new ArrayList<>();
        String upstreamGroup = properties.getKafka().getGroupId();
        groups.add(new GroupTopics(upstreamGroup, List.of(properties.getKafka().getOrderTopic(), properties.getKafka().getPaymentTopic())));
        groups.add(new GroupTopics(upstreamGroup + "-internal-events", List.of(properties.getKafka().getEventsTopic())));
        groups.add(new GroupTopics(properties.getDispatcher().getDispatcherGroupId(), List.of(
                properties.getDispatcher().getHighTopic(),
                properties.getDispatcher().getNormalTopic(),
                properties.getDispatcher().getLowTopic()
        )));
        groups.add(new GroupTopics(properties.getDispatcher().getEmailWorkerGroupId(), List.of(properties.getDispatcher().getEmailTopic())));
        groups.add(new GroupTopics(properties.getDispatcher().getSmsWorkerGroupId(), List.of(properties.getDispatcher().getSmsTopic())));
        groups.add(new GroupTopics(properties.getDispatcher().getPushWorkerGroupId(), List.of(properties.getDispatcher().getPushTopic())));
        return groups;
    }

    private record GroupTopics(String groupId, List<String> topics) {
    }
}

