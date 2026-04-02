package huynv.paymentservice.metrics;

import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.ListConsumerGroupOffsetsResult;
import org.apache.kafka.clients.admin.ListOffsetsResult;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Exposes Kafka consumer lag metrics by querying committed offsets and end offsets via AdminClient.
 */
@Component
public class KafkaConsumerLagMetrics {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerLagMetrics.class);

    private final AdminClient adminClient;
    private final String groupId;
    private final Set<String> topics;
    private final AtomicLong lagTotal;

    /**
     * Creates a lag metrics monitor for the configured consumer group and topics.
     *
     * @param meterRegistry Meter registry used to publish the consumer lag gauge.
     * @param bootstrapServers Kafka bootstrap servers.
     * @param groupId Consumer group identifier.
     * @param inventoryTopic Inventory topic name monitored for lag.
     * @param retryTopic Retry topic name monitored for lag.
     * @return Initializes consumer lag metrics.
     */
    public KafkaConsumerLagMetrics(
            MeterRegistry meterRegistry,
            @org.springframework.beans.factory.annotation.Value("${spring.kafka.bootstrap-servers}") String bootstrapServers,
            @org.springframework.beans.factory.annotation.Value("${spring.kafka.consumer.group-id}") String groupId,
            @org.springframework.beans.factory.annotation.Value("${payment.kafka.inventory-topic}") String inventoryTopic,
            @org.springframework.beans.factory.annotation.Value("${payment.kafka.retry-topic}") String retryTopic
    ) {
        Map<String, Object> config = new HashMap<>();
        config.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        this.adminClient = AdminClient.create(config);
        this.groupId = groupId;
        this.topics = Set.of(inventoryTopic, retryTopic);
        this.lagTotal = new AtomicLong(0L);
        Gauge.builder("kafka_consumer_lag", lagTotal, AtomicLong::get)
                .description("Total consumer lag for payment-service across monitored topics.")
                .register(meterRegistry);
    }

    /**
     * Periodically refreshes the total consumer lag gauge by querying Kafka for offsets.
     *
     * @return Updates the kafka_consumer_lag gauge with the latest computed value.
     */
    @Scheduled(fixedDelayString = "${payment.metrics.consumer-lag.interval-ms:15000}")
    public void refreshLag() {
        try {
            ListConsumerGroupOffsetsResult offsetsResult = adminClient.listConsumerGroupOffsets(groupId);
            Map<TopicPartition, OffsetAndMetadata> committed = offsetsResult.partitionsToOffsetAndMetadata().get(5, TimeUnit.SECONDS);

            Map<TopicPartition, OffsetAndMetadata> committedFiltered = committed.entrySet().stream()
                    .filter(e -> topics.contains(e.getKey().topic()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

            Map<TopicPartition, OffsetSpec> endOffsetReq = committedFiltered.keySet().stream()
                    .collect(Collectors.toMap(tp -> tp, tp -> OffsetSpec.latest()));

            ListOffsetsResult endOffsetsResult = adminClient.listOffsets(endOffsetReq);

            long total = 0L;
            for (TopicPartition tp : committedFiltered.keySet()) {
                long committedOffset = committedFiltered.get(tp).offset();
                long endOffset = endOffsetsResult.partitionResult(tp).get(5, TimeUnit.SECONDS).offset();
                long lag = Math.max(0L, endOffset - committedOffset);
                total += lag;
            }
            lagTotal.set(total);
        } catch (Exception e) {
            log.warn("Failed to refresh consumer lag metrics for groupId={}. error={}", groupId, e.getMessage());
        }
    }
}

