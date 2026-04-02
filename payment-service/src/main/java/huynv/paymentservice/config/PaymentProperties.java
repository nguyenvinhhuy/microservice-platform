package huynv.paymentservice.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Defines strongly-typed configuration properties for the payment service.
 */
@ConfigurationProperties(prefix = "payment")
public class PaymentProperties {

    private final Processing processing = new Processing();
    private final Outbox outbox = new Outbox();
    private final Kafka kafka = new Kafka();

    /**
     * Returns payment processing feature flag configuration.
     *
     * @return Processing configuration.
     */
    public Processing getProcessing() {
        return processing;
    }

    /**
     * Returns outbox publishing feature flag configuration.
     *
     * @return Outbox configuration.
     */
    public Outbox getOutbox() {
        return outbox;
    }

    /**
     * Returns Kafka integration configuration for inbound and outbound topics.
     *
     * @return Kafka configuration.
     */
    public Kafka getKafka() {
        return kafka;
    }

    /**
     * Holds kill switch configuration for payment processing.
     */
    public static final class Processing {
        private boolean enabled = true;

        /**
         * Returns whether payment processing is enabled.
         *
         * @return True when payment processing is enabled.
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets whether payment processing is enabled.
         *
         * @param enabled Flag value.
         * @return Updates the processing enabled flag.
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Holds kill switch and scheduling configuration for outbox publishing.
     */
    public static final class Outbox {
        private boolean enabled = true;
        private long publisherDelayMs = 2000;
        private int publisherBatchSize = 50;

        /**
         * Returns whether outbox publishing is enabled.
         *
         * @return True when outbox publishing is enabled.
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets whether outbox publishing is enabled.
         *
         * @param enabled Flag value.
         * @return Updates the outbox enabled flag.
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Returns publisher fixed-delay interval in milliseconds.
         *
         * @return Delay in milliseconds.
         */
        public long getPublisherDelayMs() {
            return publisherDelayMs;
        }

        /**
         * Sets publisher fixed-delay interval in milliseconds.
         *
         * @param publisherDelayMs Delay in milliseconds.
         * @return Updates the outbox publisher delay configuration.
         */
        public void setPublisherDelayMs(long publisherDelayMs) {
            this.publisherDelayMs = publisherDelayMs;
        }

        /**
         * Returns the maximum number of outbox records published per cycle.
         *
         * @return Batch size.
         */
        public int getPublisherBatchSize() {
            return publisherBatchSize;
        }

        /**
         * Sets the maximum number of outbox records published per cycle.
         *
         * @param publisherBatchSize Batch size.
         * @return Updates the outbox publisher batch size configuration.
         */
        public void setPublisherBatchSize(int publisherBatchSize) {
            this.publisherBatchSize = publisherBatchSize;
        }
    }

    /**
     * Holds Kafka topic names used by the payment service.
     */
    public static final class Kafka {
        private String inventoryTopic = "inventory.events";
        private String eventsTopic = "payment.events";
        private String retryTopic = "inventory.events.retry";
        private String dlqTopic = "inventory.events.dlq";

        /**
         * Returns the inventory reserved topic name used for inbound saga events.
         *
         * @return Inventory topic name.
         */
        public String getInventoryTopic() {
            return inventoryTopic;
        }

        /**
         * Sets the inventory reserved topic name used for inbound saga events.
         *
         * @param inventoryTopic Inventory topic name.
         * @return Updates the inventory topic name configuration.
         */
        public void setInventoryTopic(String inventoryTopic) {
            this.inventoryTopic = inventoryTopic;
        }

        /**
         * Returns the payment domain events topic name used for outbound events.
         *
         * @return Events topic name.
         */
        public String getEventsTopic() {
            return eventsTopic;
        }

        /**
         * Sets the payment domain events topic name used for outbound events.
         *
         * @param eventsTopic Events topic name.
         * @return Updates the events topic name configuration.
         */
        public void setEventsTopic(String eventsTopic) {
            this.eventsTopic = eventsTopic;
        }

        /**
         * Returns the retry topic name used for transient consumer failures.
         *
         * @return Retry topic name.
         */
        public String getRetryTopic() {
            return retryTopic;
        }

        /**
         * Sets the retry topic name used for transient consumer failures.
         *
         * @param retryTopic Retry topic name.
         * @return Updates the retry topic name configuration.
         */
        public void setRetryTopic(String retryTopic) {
            this.retryTopic = retryTopic;
        }

        /**
         * Returns the dead-letter topic name used for poison messages.
         *
         * @return Dead-letter topic name.
         */
        public String getDlqTopic() {
            return dlqTopic;
        }

        /**
         * Sets the dead-letter topic name used for poison messages.
         *
         * @param dlqTopic Dead-letter topic name.
         * @return Updates the dead-letter topic name configuration.
         */
        public void setDlqTopic(String dlqTopic) {
            this.dlqTopic = dlqTopic;
        }
    }
}
