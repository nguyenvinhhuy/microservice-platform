package huynv.eventinfra.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Defines configuration properties for notification-service behavior and integrations.
 */
@Validated
@ConfigurationProperties(prefix = "notification")
public class NotificationProperties {

    private final Dlq dlq = new Dlq();
    private final Dispatch dispatch = new Dispatch();
    private final Kafka kafka = new Kafka();
    private final Retry retry = new Retry();
    private final Channels channels = new Channels();
    private final Email email = new Email();
    private final Sms sms = new Sms();
    private final Push push = new Push();
    private final Integrations integrations = new Integrations();
    private final RedisCache redisCache = new RedisCache();
    private final Dispatcher dispatcher = new Dispatcher();
    private final Workers workers = new Workers();
    private final RateLimits rateLimits = new RateLimits();
    private final Templates templates = new Templates();
    private final Outbox outbox = new Outbox();
    private final SyntheticContacts syntheticContacts = new SyntheticContacts();

    /**
     * Returns DLQ configuration used for replay protection and operational safety controls.
     *
     * @return Returns DLQ configuration properties.
     */
    public Dlq getDlq() {
        return dlq;
    }

    /**
     * Returns dispatch enablement configuration used as an operational safety switch.
     *
     * @return Returns dispatch enablement configuration.
     */
    public Dispatch getDispatch() {
        return dispatch;
    }

    /**
     * Returns Kafka configuration properties for notification-service consumers and DLQ handling.
     *
     * @return Returns Kafka configuration properties for notification processing.
     */
    public Kafka getKafka() {
        return kafka;
    }

    /**
     * Returns retry policy configuration for Kafka consumer error handling.
     *
     * @return Returns retry policy configuration for failed notification processing.
     */
    public Retry getRetry() {
        return retry;
    }

    /**
     * Returns channel enablement flags for the notification strategy layer.
     *
     * @return Returns the channel configuration controlling which delivery channels are active.
     */
    public Channels getChannels() {
        return channels;
    }

    /**
     * Returns email-specific configuration used by the email notification channel.
     *
     * @return Returns email configuration such as sender address and SMTP enablement.
     */
    public Email getEmail() {
        return email;
    }

    /**
     * Returns SMS channel enablement configuration used as an operational safety switch.
     *
     * @return Returns SMS channel configuration.
     */
    public Sms getSms() {
        return sms;
    }

    /**
     * Returns push channel enablement configuration used as an operational safety switch.
     *
     * @return Returns push channel configuration.
     */
    public Push getPush() {
        return push;
    }

    /**
     * Returns integration configuration for calling trusted internal services.
     *
     * @return Returns integration configuration for recipient and contact resolution.
     */
    public Integrations getIntegrations() {
        return integrations;
    }

    /**
     * Returns Redis caching configuration for contact and recipient resolution.
     *
     * @return Returns Redis cache configuration.
     */
    public RedisCache getRedisCache() {
        return redisCache;
    }

    /**
     * Returns dispatcher configuration for internal priority queues and channel worker topics.
     *
     * @return Returns dispatcher configuration.
     */
    public Dispatcher getDispatcher() {
        return dispatcher;
    }

    /**
     * Returns worker pool sizing and queue capacity configuration for channel execution isolation.
     *
     * @return Returns worker configuration for executor isolation and backpressure protection.
     */
    public Workers getWorkers() {
        return workers;
    }

    /**
     * Returns per-channel rate limit configuration used for external provider protection.
     *
     * @return Returns per-channel rate limit configuration.
     */
    public RateLimits getRateLimits() {
        return rateLimits;
    }

    /**
     * Returns template configuration for tenant overrides and versioning behavior.
     *
     * @return Returns template configuration.
     */
    public Templates getTemplates() {
        return templates;
    }

    /**
     * Returns Kafka outbox configuration used for reliable asynchronous publishing.
     *
     * @return Returns outbox configuration such as publisher batch size and enablement flags.
     */
    public Outbox getOutbox() {
        return outbox;
    }

    /**
     * Returns synthetic contact configuration used to guard non-production fallbacks.
     *
     * @return Returns synthetic contact configuration.
     */
    public SyntheticContacts getSyntheticContacts() {
        return syntheticContacts;
    }

    /**
     * Defines Kafka-related properties for the notification service.
     */
    public static final class Kafka {
        private String orderTopic = "order.events";
        private String paymentTopic = "payment.events";
        private String eventsTopic = "notification.events";
        private String retry1mTopic = "notification.retry.1m";
        private String retry5mTopic = "notification.retry.5m";
        private String retry30mTopic = "notification.retry.30m";
        private String dlqTopic = "notification.dlq";
        private String groupId = "notification-service";
        private boolean consumerEnabled = true;
        @Min(1)
        @Max(64)
        private int listenerConcurrency = 6;

        /**
         * Returns the Kafka topic name used for order event consumption.
         *
         * @return Returns the order events topic name.
         */
        public String getOrderTopic() {
            return orderTopic;
        }

        /**
         * Sets the Kafka topic name used for order event consumption.
         *
         * @param orderTopic Kafka topic name to consume order events from.
         * @return Updates the order topic configuration.
         */
        public void setOrderTopic(String orderTopic) {
            this.orderTopic = orderTopic;
        }

        /**
         * Returns the Kafka topic name used for payment event consumption.
         *
         * @return Returns the payment events topic name.
         */
        public String getPaymentTopic() {
            return paymentTopic;
        }

        /**
         * Sets the Kafka topic name used for payment event consumption.
         *
         * @param paymentTopic Kafka topic name to consume payment events from.
         * @return Updates the payment topic configuration.
         */
        public void setPaymentTopic(String paymentTopic) {
            this.paymentTopic = paymentTopic;
        }

        /**
         * Returns the internal notification events topic used as a durable ingestion buffer.
         *
         * @return Returns the internal notification events topic name.
         */
        public String getEventsTopic() {
            return eventsTopic;
        }

        /**
         * Sets the internal notification events topic used as a durable ingestion buffer.
         *
         * @param eventsTopic Internal notification events topic name.
         * @return Updates the internal notification events topic configuration.
         */
        public void setEventsTopic(String eventsTopic) {
            this.eventsTopic = eventsTopic;
        }

        /**
         * Returns the retry tier topic used for 1 minute retry delays.
         *
         * @return Returns the 1 minute retry topic name.
         */
        public String getRetry1mTopic() {
            return retry1mTopic;
        }

        /**
         * Sets the retry tier topic used for 1 minute retry delays.
         *
         * @param retry1mTopic 1 minute retry topic name.
         * @return Updates the 1 minute retry topic configuration.
         */
        public void setRetry1mTopic(String retry1mTopic) {
            this.retry1mTopic = retry1mTopic;
        }

        /**
         * Returns the retry tier topic used for 5 minute retry delays.
         *
         * @return Returns the 5 minute retry topic name.
         */
        public String getRetry5mTopic() {
            return retry5mTopic;
        }

        /**
         * Sets the retry tier topic used for 5 minute retry delays.
         *
         * @param retry5mTopic 5 minute retry topic name.
         * @return Updates the 5 minute retry topic configuration.
         */
        public void setRetry5mTopic(String retry5mTopic) {
            this.retry5mTopic = retry5mTopic;
        }

        /**
         * Returns the retry tier topic used for 30 minute retry delays.
         *
         * @return Returns the 30 minute retry topic name.
         */
        public String getRetry30mTopic() {
            return retry30mTopic;
        }

        /**
         * Sets the retry tier topic used for 30 minute retry delays.
         *
         * @param retry30mTopic 30 minute retry topic name.
         * @return Updates the 30 minute retry topic configuration.
         */
        public void setRetry30mTopic(String retry30mTopic) {
            this.retry30mTopic = retry30mTopic;
        }

        /**
         * Returns the dead-letter topic name used for poison message isolation.
         *
         * @return Returns the configured DLQ topic name.
         */
        public String getDlqTopic() {
            return dlqTopic;
        }

        /**
         * Sets the dead-letter topic name used for poison message isolation.
         *
         * @param dlqTopic Kafka topic name for dead-letter messages.
         * @return Updates the DLQ topic configuration.
         */
        public void setDlqTopic(String dlqTopic) {
            this.dlqTopic = dlqTopic;
        }

        /**
         * Returns the Kafka consumer group id used by this service.
         *
         * @return Returns the Kafka consumer group identifier.
         */
        public String getGroupId() {
            return groupId;
        }

        /**
         * Sets the Kafka consumer group id used by this service.
         *
         * @param groupId Consumer group identifier for notification-service.
         * @return Updates the consumer group identifier.
         */
        public void setGroupId(String groupId) {
            this.groupId = groupId;
        }

        /**
         * Returns whether Kafka consumers are enabled for this service.
         *
         * @return Returns true when Kafka listeners should be started.
         */
        public boolean isConsumerEnabled() {
            return consumerEnabled;
        }

        /**
         * Enables or disables Kafka consumers for this service.
         *
         * @param consumerEnabled Flag indicating whether consumers are enabled.
         * @return Updates the consumer enablement flag.
         */
        public void setConsumerEnabled(boolean consumerEnabled) {
            this.consumerEnabled = consumerEnabled;
        }

        /**
         * Returns the default listener concurrency used by Kafka listener containers.
         *
         * @return Returns the listener concurrency value.
         */
        public int getListenerConcurrency() {
            return listenerConcurrency;
        }

        /**
         * Sets the default listener concurrency used by Kafka listener containers.
         *
         * @param listenerConcurrency Concurrency value applied to listener containers.
         * @return Updates the listener concurrency configuration.
         */
        public void setListenerConcurrency(int listenerConcurrency) {
            this.listenerConcurrency = listenerConcurrency;
        }
    }

    /**
     * Defines configuration for the transactional Kafka outbox publisher.
     */
        public static final class Outbox {
            private final Publisher publisher = new Publisher();

        /**
         * Returns publisher configuration controlling batch size, delays, and enablement.
         *
         * @return Returns publisher configuration.
         */
        public Publisher getPublisher() {
            return publisher;
        }

        /**
         * Defines Kafka outbox publisher settings.
         */
        public static final class Publisher {
            private boolean enabled = true;
            @Min(10)
            @Max(5000)
            private int batchSize = 500;
            @Min(100)
            private long fixedDelayMs = 250L;
            @Min(100)
            private long sendTimeoutMs = 10_000L;
            @Min(10)
            @Max(50000)
            private int maxInflight = 2000;
            @Min(1000)
            private long processingTimeoutMs = 60_000L;

            /**
             * Returns whether the outbox publisher is enabled.
             *
             * @return Returns true when background publishing is enabled.
             */
            public boolean isEnabled() {
                return enabled;
            }

            /**
             * Sets whether the outbox publisher is enabled.
             *
             * @param enabled Flag controlling publisher execution.
             * @return Updates the publisher enabled flag.
             */
            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }

            /**
             * Returns the maximum number of outbox messages claimed per publisher iteration.
             *
             * @return Returns the maximum claimed batch size.
             */
            public int getBatchSize() {
                return batchSize;
            }

            /**
             * Sets the maximum number of outbox messages claimed per publisher iteration.
             *
             * @param batchSize Maximum number of messages per batch.
             * @return Updates the batch size configuration.
             */
            public void setBatchSize(int batchSize) {
                this.batchSize = batchSize;
            }

            /**
             * Returns the fixed delay between outbox publisher iterations in milliseconds.
             *
             * @return Returns the fixed delay in milliseconds.
             */
            public long getFixedDelayMs() {
                return fixedDelayMs;
            }

            /**
             * Sets the fixed delay between outbox publisher iterations in milliseconds.
             *
             * @param fixedDelayMs Delay in milliseconds.
             * @return Updates the fixed delay configuration.
             */
            public void setFixedDelayMs(long fixedDelayMs) {
                this.fixedDelayMs = fixedDelayMs;
            }

            /**
             * Returns the maximum time to wait for broker acknowledgment per send.
             *
             * @return Returns the send timeout in milliseconds.
             */
            public long getSendTimeoutMs() {
                return sendTimeoutMs;
            }

            /**
             * Sets the maximum time to wait for broker acknowledgment per send.
             *
             * @param sendTimeoutMs Send timeout in milliseconds.
             * @return Updates the send timeout configuration.
             */
            public void setSendTimeoutMs(long sendTimeoutMs) {
                this.sendTimeoutMs = sendTimeoutMs;
            }

            /**
             * Returns the maximum number of Kafka sends allowed to be in-flight at once.
             *
             * @return Returns the maximum number of in-flight sends.
             */
            public int getMaxInflight() {
                return maxInflight;
            }

            /**
             * Sets the maximum number of Kafka sends allowed to be in-flight at once.
             *
             * @param maxInflight Maximum number of in-flight sends.
             * @return Updates the in-flight send cap configuration.
             */
            public void setMaxInflight(int maxInflight) {
                this.maxInflight = maxInflight;
            }

            /**
             * Returns the duration after which a PROCESSING outbox row is treated as stale and eligible for re-claim.
             *
             * @return Returns the processing timeout in milliseconds.
             */
            public long getProcessingTimeoutMs() {
                return processingTimeoutMs;
            }

            /**
             * Sets the duration after which a PROCESSING outbox row is treated as stale and eligible for re-claim.
             *
             * @param processingTimeoutMs Processing timeout in milliseconds.
             * @return Updates the processing timeout configuration.
             */
            public void setProcessingTimeoutMs(long processingTimeoutMs) {
                this.processingTimeoutMs = processingTimeoutMs;
            }
        }
    }

    /**
     * Defines configuration for synthetic contacts used only in non-production environments.
     */
    public static final class SyntheticContacts {
        private boolean enabled = false;

        /**
         * Returns whether synthetic contacts are enabled.
         *
         * @return Returns true when synthetic contacts may be used as a fallback.
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Sets whether synthetic contacts are enabled.
         *
         * @param enabled Flag controlling synthetic contact fallback behavior.
         * @return Updates the synthetic contacts enabled flag.
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Defines retry policy configuration for transient processing failures.
     */
    public static final class Retry {
        @Min(1)
        @Max(20)
        private int maxAttempts = 5;
        @Min(1)
        private long initialIntervalMs = 250L;
        @Min(1)
        private double multiplier = 2.0;
        @Min(1)
        private long maxIntervalMs = 5_000L;

        /**
         * Returns the maximum processing attempts including the initial attempt.
         *
         * @return Returns the maximum number of attempts before sending to DLQ.
         */
        public int getMaxAttempts() {
            return maxAttempts;
        }

        /**
         * Sets the maximum processing attempts including the initial attempt.
         *
         * @param maxAttempts Maximum number of attempts before sending to DLQ.
         * @return Updates the maximum attempts configuration.
         */
        public void setMaxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
        }

        /**
         * Returns the initial backoff interval in milliseconds.
         *
         * @return Returns the initial backoff interval in milliseconds.
         */
        public long getInitialIntervalMs() {
            return initialIntervalMs;
        }

        /**
         * Sets the initial backoff interval in milliseconds.
         *
         * @param initialIntervalMs Initial backoff interval in milliseconds.
         * @return Updates the initial backoff interval configuration.
         */
        public void setInitialIntervalMs(long initialIntervalMs) {
            this.initialIntervalMs = initialIntervalMs;
        }

        /**
         * Returns the exponential backoff multiplier applied after each failure.
         *
         * @return Returns the backoff multiplier.
         */
        public double getMultiplier() {
            return multiplier;
        }

        /**
         * Sets the exponential backoff multiplier applied after each failure.
         *
         * @param multiplier Backoff multiplier applied after each failure.
         * @return Updates the backoff multiplier configuration.
         */
        public void setMultiplier(double multiplier) {
            this.multiplier = multiplier;
        }

        /**
         * Returns the maximum backoff interval in milliseconds.
         *
         * @return Returns the maximum backoff interval in milliseconds.
         */
        public long getMaxIntervalMs() {
            return maxIntervalMs;
        }

        /**
         * Sets the maximum backoff interval in milliseconds.
         *
         * @param maxIntervalMs Maximum backoff interval in milliseconds.
         * @return Updates the maximum backoff interval configuration.
         */
        public void setMaxIntervalMs(long maxIntervalMs) {
            this.maxIntervalMs = maxIntervalMs;
        }
    }

    /**
     * Defines which notification channels are active at runtime.
     */
    public static final class Channels {
        private boolean emailEnabled = true;
        private boolean smsEnabled = false;
        private boolean pushEnabled = false;

        /**
         * Returns whether email delivery is enabled.
         *
         * @return Returns true when email notifications should be sent.
         */
        public boolean isEmailEnabled() {
            return emailEnabled;
        }

        /**
         * Enables or disables email delivery.
         *
         * @param emailEnabled Flag indicating whether email notifications are enabled.
         * @return Updates email channel enablement.
         */
        public void setEmailEnabled(boolean emailEnabled) {
            this.emailEnabled = emailEnabled;
        }

        /**
         * Returns whether SMS delivery is enabled.
         *
         * @return Returns true when SMS notifications should be sent.
         */
        public boolean isSmsEnabled() {
            return smsEnabled;
        }

        /**
         * Enables or disables SMS delivery.
         *
         * @param smsEnabled Flag indicating whether SMS notifications are enabled.
         * @return Updates SMS channel enablement.
         */
        public void setSmsEnabled(boolean smsEnabled) {
            this.smsEnabled = smsEnabled;
        }

        /**
         * Returns whether push delivery is enabled.
         *
         * @return Returns true when push notifications should be sent.
         */
        public boolean isPushEnabled() {
            return pushEnabled;
        }

        /**
         * Enables or disables push delivery.
         *
         * @param pushEnabled Flag indicating whether push notifications are enabled.
         * @return Updates push channel enablement.
         */
        public void setPushEnabled(boolean pushEnabled) {
            this.pushEnabled = pushEnabled;
        }
    }

    /**
     * Defines email-related configuration used by the EmailChannel implementation.
     */
    public static final class Email {
        private boolean enabled = true;
        private boolean smtpEnabled = false;
        private String fromAddress = "no-reply@platform.local";

        /**
         * Returns whether the email channel is enabled as an operational safety control.
         *
         * @return Returns true when email delivery is enabled.
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Enables or disables the email channel as an operational safety control.
         *
         * @param enabled Flag indicating whether email delivery is enabled.
         * @return Updates the email channel enablement configuration.
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Returns whether SMTP delivery is enabled for email notifications.
         *
         * @return Returns true when the EmailChannel should send via SMTP.
         */
        public boolean isSmtpEnabled() {
            return smtpEnabled;
        }

        /**
         * Enables or disables SMTP delivery for email notifications.
         *
         * @param smtpEnabled Flag indicating whether SMTP delivery is enabled.
         * @return Updates SMTP delivery enablement.
         */
        public void setSmtpEnabled(boolean smtpEnabled) {
            this.smtpEnabled = smtpEnabled;
        }

        /**
         * Returns the configured sender address for emails.
         *
         * @return Returns the sender address for outbound email.
         */
        public String getFromAddress() {
            return fromAddress;
        }

        /**
         * Sets the configured sender address for emails.
         *
         * @param fromAddress Sender address used for outbound email.
         * @return Updates the email sender address configuration.
         */
        public void setFromAddress(String fromAddress) {
            this.fromAddress = fromAddress;
        }
    }

    /**
     * Defines integration endpoints for trusted internal services.
     */
    public static final class Integrations {
        private String orderViewBaseUrl = "http://order-view-service:8087";
        private String userServiceBaseUrl = "http://user-service:8090";
        @Min(50)
        private long connectTimeoutMs = 500L;
        @Min(50)
        private long readTimeoutMs = 2_000L;

        /**
         * Returns the base URL for order-view-service queries.
         *
         * @return Returns the order-view-service base URL.
         */
        public String getOrderViewBaseUrl() {
            return orderViewBaseUrl;
        }

        /**
         * Sets the base URL for order-view-service queries.
         *
         * @param orderViewBaseUrl Base URL for order-view-service.
         * @return Updates the order-view-service base URL.
         */
        public void setOrderViewBaseUrl(String orderViewBaseUrl) {
            this.orderViewBaseUrl = orderViewBaseUrl;
        }

        /**
         * Returns the base URL for user-service queries.
         *
         * @return Returns the user-service base URL.
         */
        public String getUserServiceBaseUrl() {
            return userServiceBaseUrl;
        }

        /**
         * Sets the base URL for user-service queries.
         *
         * @param userServiceBaseUrl Base URL for user-service.
         * @return Updates the user-service base URL.
         */
        public void setUserServiceBaseUrl(String userServiceBaseUrl) {
            this.userServiceBaseUrl = userServiceBaseUrl;
        }

        /**
         * Returns the connect timeout applied to internal REST calls in milliseconds.
         *
         * @return Returns the connect timeout in milliseconds.
         */
        public long getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        /**
         * Sets the connect timeout applied to internal REST calls in milliseconds.
         *
         * @param connectTimeoutMs Connect timeout in milliseconds.
         * @return Updates the connect timeout configuration.
         */
        public void setConnectTimeoutMs(long connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        /**
         * Returns the read timeout applied to internal REST calls in milliseconds.
         *
         * @return Returns the read timeout in milliseconds.
         */
        public long getReadTimeoutMs() {
            return readTimeoutMs;
        }

        /**
         * Sets the read timeout applied to internal REST calls in milliseconds.
         *
         * @param readTimeoutMs Read timeout in milliseconds.
         * @return Updates the read timeout configuration.
         */
        public void setReadTimeoutMs(long readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }

    /**
     * Defines Redis caching configuration for contact resolution and rate limiting.
     */
    public static final class RedisCache {
        private boolean enabled = true;

        @Min(1)
        @Max(86400)
        private int contactTtlSeconds = 900;

        /**
         * Returns whether Redis caching is enabled.
         *
         * @return Returns true when Redis caching should be used.
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Enables or disables Redis caching.
         *
         * @param enabled Flag indicating whether Redis caching is enabled.
         * @return Updates Redis caching enablement.
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Returns the TTL in seconds used for contact cache entries.
         *
         * @return Returns the contact cache TTL in seconds.
         */
        public int getContactTtlSeconds() {
            return contactTtlSeconds;
        }

        /**
         * Sets the TTL in seconds used for contact cache entries.
         *
         * @param contactTtlSeconds Contact cache TTL in seconds.
         * @return Updates the contact cache TTL configuration.
         */
        public void setContactTtlSeconds(int contactTtlSeconds) {
            this.contactTtlSeconds = contactTtlSeconds;
        }
    }

    /**
     * Defines internal dispatcher configuration for priority queues and channel worker topics.
     */
    public static final class Dispatcher {
        private boolean enabled = false;
        private String dispatcherGroupId = "notification-dispatcher";
        private String highTopic = "notification.high";
        private String normalTopic = "notification.normal";
        private String lowTopic = "notification.low";
        private final PriorityWeights priorityWeights = new PriorityWeights();
        private String emailTopic = "notification.email";
        private String smsTopic = "notification.sms";
        private String pushTopic = "notification.push";
        private String emailWorkerGroupId = "notification-email-worker";
        private String smsWorkerGroupId = "notification-sms-worker";
        private String pushWorkerGroupId = "notification-push-worker";

        /**
         * Returns whether the dispatcher layer is enabled.
         *
         * @return Returns true when dispatcher consumers should be started.
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Enables or disables the dispatcher layer.
         *
         * @param enabled Flag indicating whether dispatcher consumers are enabled.
         * @return Updates dispatcher enablement.
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        /**
         * Returns the dispatcher consumer group id.
         *
         * @return Returns the dispatcher consumer group id.
         */
        public String getDispatcherGroupId() {
            return dispatcherGroupId;
        }

        /**
         * Sets the dispatcher consumer group id.
         *
         * @param dispatcherGroupId Dispatcher consumer group id.
         * @return Updates the dispatcher consumer group id.
         */
        public void setDispatcherGroupId(String dispatcherGroupId) {
            this.dispatcherGroupId = dispatcherGroupId;
        }

        /**
         * Returns the high priority topic name.
         *
         * @return Returns the high priority topic name.
         */
        public String getHighTopic() {
            return highTopic;
        }

        /**
         * Sets the high priority topic name.
         *
         * @param highTopic High priority topic name.
         * @return Updates the high priority topic configuration.
         */
        public void setHighTopic(String highTopic) {
            this.highTopic = highTopic;
        }

        /**
         * Returns the normal priority topic name.
         *
         * @return Returns the normal priority topic name.
         */
        public String getNormalTopic() {
            return normalTopic;
        }

        /**
         * Sets the normal priority topic name.
         *
         * @param normalTopic Normal priority topic name.
         * @return Updates the normal priority topic configuration.
         */
        public void setNormalTopic(String normalTopic) {
            this.normalTopic = normalTopic;
        }

        /**
         * Returns the low priority topic name.
         *
         * @return Returns the low priority topic name.
         */
        public String getLowTopic() {
            return lowTopic;
        }

        /**
         * Sets the low priority topic name.
         *
         * @param lowTopic Low priority topic name.
         * @return Updates the low priority topic configuration.
         */
        public void setLowTopic(String lowTopic) {
            this.lowTopic = lowTopic;
        }

        /**
         * Returns the priority topic concurrency weights used to prevent starvation.
         *
         * @return Returns the configured priority weights.
         */
        public PriorityWeights getPriorityWeights() {
            return priorityWeights;
        }

        /**
         * Returns the email worker topic name.
         *
         * @return Returns the email worker topic name.
         */
        public String getEmailTopic() {
            return emailTopic;
        }

        /**
         * Sets the email worker topic name.
         *
         * @param emailTopic Email worker topic name.
         * @return Updates the email worker topic configuration.
         */
        public void setEmailTopic(String emailTopic) {
            this.emailTopic = emailTopic;
        }

        /**
         * Returns the SMS worker topic name.
         *
         * @return Returns the SMS worker topic name.
         */
        public String getSmsTopic() {
            return smsTopic;
        }

        /**
         * Sets the SMS worker topic name.
         *
         * @param smsTopic SMS worker topic name.
         * @return Updates the SMS worker topic configuration.
         */
        public void setSmsTopic(String smsTopic) {
            this.smsTopic = smsTopic;
        }

        /**
         * Returns the push worker topic name.
         *
         * @return Returns the push worker topic name.
         */
        public String getPushTopic() {
            return pushTopic;
        }

        /**
         * Sets the push worker topic name.
         *
         * @param pushTopic Push worker topic name.
         * @return Updates the push worker topic configuration.
         */
        public void setPushTopic(String pushTopic) {
            this.pushTopic = pushTopic;
        }

        /**
         * Returns the email worker consumer group id.
         *
         * @return Returns the email worker consumer group id.
         */
        public String getEmailWorkerGroupId() {
            return emailWorkerGroupId;
        }

        /**
         * Sets the email worker consumer group id.
         *
         * @param emailWorkerGroupId Email worker consumer group id.
         * @return Updates the email worker consumer group id configuration.
         */
        public void setEmailWorkerGroupId(String emailWorkerGroupId) {
            this.emailWorkerGroupId = emailWorkerGroupId;
        }

        /**
         * Returns the SMS worker consumer group id.
         *
         * @return Returns the SMS worker consumer group id.
         */
        public String getSmsWorkerGroupId() {
            return smsWorkerGroupId;
        }

        /**
         * Sets the SMS worker consumer group id.
         *
         * @param smsWorkerGroupId SMS worker consumer group id.
         * @return Updates the SMS worker consumer group id configuration.
         */
        public void setSmsWorkerGroupId(String smsWorkerGroupId) {
            this.smsWorkerGroupId = smsWorkerGroupId;
        }

        /**
         * Returns the push worker consumer group id.
         *
         * @return Returns the push worker consumer group id.
         */
        public String getPushWorkerGroupId() {
            return pushWorkerGroupId;
        }

        /**
         * Sets the push worker consumer group id.
         *
         * @param pushWorkerGroupId Push worker consumer group id.
         * @return Updates the push worker consumer group id configuration.
         */
        public void setPushWorkerGroupId(String pushWorkerGroupId) {
            this.pushWorkerGroupId = pushWorkerGroupId;
        }

        /**
         * Defines dispatcher weights used to allocate worker capacity fairly across priority topics.
         */
        public static final class PriorityWeights {
            @Min(1)
            @Max(64)
            private int high = 5;

            @Min(1)
            @Max(64)
            private int normal = 3;

            @Min(1)
            @Max(64)
            private int low = 1;

            /**
             * Returns the high priority weight used for proportional polling capacity.
             *
             * @return Returns the high priority weight.
             */
            public int getHigh() {
                return high;
            }

            /**
             * Sets the high priority weight used for proportional polling capacity.
             *
             * @param high High priority weight value.
             * @return Updates the high priority weight configuration.
             */
            public void setHigh(int high) {
                this.high = high;
            }

            /**
             * Returns the normal priority weight used for proportional polling capacity.
             *
             * @return Returns the normal priority weight.
             */
            public int getNormal() {
                return normal;
            }

            /**
             * Sets the normal priority weight used for proportional polling capacity.
             *
             * @param normal Normal priority weight value.
             * @return Updates the normal priority weight configuration.
             */
            public void setNormal(int normal) {
                this.normal = normal;
            }

            /**
             * Returns the low priority weight used for proportional polling capacity.
             *
             * @return Returns the low priority weight.
             */
            public int getLow() {
                return low;
            }

            /**
             * Sets the low priority weight used for proportional polling capacity.
             *
             * @param low Low priority weight value.
             * @return Updates the low priority weight configuration.
             */
            public void setLow(int low) {
                this.low = low;
            }
        }
    }

    /**
     * Defines worker pool sizing and queue capacity settings used to enforce backpressure and isolation.
     */
    public static final class Workers {
        @Min(1)
        @Max(256)
        private int emailPoolSize = 8;

        @Min(1)
        @Max(20000)
        private int emailQueueSize = 1000;

        @Min(1)
        @Max(256)
        private int smsPoolSize = 4;

        @Min(1)
        @Max(20000)
        private int smsQueueSize = 500;

        @Min(1)
        @Max(256)
        private int pushPoolSize = 4;

        @Min(1)
        @Max(20000)
        private int pushQueueSize = 500;

        /**
         * Returns the email provider worker pool size.
         *
         * @return Returns the email provider worker pool size.
         */
        public int getEmailPoolSize() {
            return emailPoolSize;
        }

        /**
         * Sets the email provider worker pool size.
         *
         * @param emailPoolSize Email provider worker pool size.
         * @return Updates the email worker pool size configuration.
         */
        public void setEmailPoolSize(int emailPoolSize) {
            this.emailPoolSize = emailPoolSize;
        }

        /**
         * Returns the bounded queue size for email provider calls.
         *
         * @return Returns the email worker queue size.
         */
        public int getEmailQueueSize() {
            return emailQueueSize;
        }

        /**
         * Sets the bounded queue size for email provider calls.
         *
         * @param emailQueueSize Email worker queue size.
         * @return Updates the email worker queue size configuration.
         */
        public void setEmailQueueSize(int emailQueueSize) {
            this.emailQueueSize = emailQueueSize;
        }

        /**
         * Returns the SMS provider worker pool size.
         *
         * @return Returns the SMS provider worker pool size.
         */
        public int getSmsPoolSize() {
            return smsPoolSize;
        }

        /**
         * Sets the SMS provider worker pool size.
         *
         * @param smsPoolSize SMS provider worker pool size.
         * @return Updates the SMS worker pool size configuration.
         */
        public void setSmsPoolSize(int smsPoolSize) {
            this.smsPoolSize = smsPoolSize;
        }

        /**
         * Returns the bounded queue size for SMS provider calls.
         *
         * @return Returns the SMS worker queue size.
         */
        public int getSmsQueueSize() {
            return smsQueueSize;
        }

        /**
         * Sets the bounded queue size for SMS provider calls.
         *
         * @param smsQueueSize SMS worker queue size.
         * @return Updates the SMS worker queue size configuration.
         */
        public void setSmsQueueSize(int smsQueueSize) {
            this.smsQueueSize = smsQueueSize;
        }

        /**
         * Returns the push provider worker pool size.
         *
         * @return Returns the push provider worker pool size.
         */
        public int getPushPoolSize() {
            return pushPoolSize;
        }

        /**
         * Sets the push provider worker pool size.
         *
         * @param pushPoolSize Push provider worker pool size.
         * @return Updates the push worker pool size configuration.
         */
        public void setPushPoolSize(int pushPoolSize) {
            this.pushPoolSize = pushPoolSize;
        }

        /**
         * Returns the bounded queue size for push provider calls.
         *
         * @return Returns the push worker queue size.
         */
        public int getPushQueueSize() {
            return pushQueueSize;
        }

        /**
         * Sets the bounded queue size for push provider calls.
         *
         * @param pushQueueSize Push worker queue size.
         * @return Updates the push worker queue size configuration.
         */
        public void setPushQueueSize(int pushQueueSize) {
            this.pushQueueSize = pushQueueSize;
        }
    }

    /**
     * Defines per-channel rate limits for external provider protection.
     */
    public static final class RateLimits {
        @Min(1)
        @Max(100000)
        private int emailPerSecond = 50;

        @Min(1)
        @Max(100000)
        private int emailBurst = 50;

        @Min(1)
        @Max(100000)
        private int smsPerSecond = 20;

        @Min(1)
        @Max(100000)
        private int smsBurst = 20;

        @Min(1)
        @Max(100000)
        private int pushPerSecond = 100;

        @Min(1)
        @Max(100000)
        private int pushBurst = 100;

        private boolean failOpenOnError = false;

        /**
         * Returns the allowed email sends per second.
         *
         * @return Returns the email rate limit per second.
         */
        public int getEmailPerSecond() {
            return emailPerSecond;
        }

        /**
         * Sets the allowed email sends per second.
         *
         * @param emailPerSecond Email sends per second.
         * @return Updates the email rate limit configuration.
         */
        public void setEmailPerSecond(int emailPerSecond) {
            this.emailPerSecond = emailPerSecond;
        }

        /**
         * Returns the burst capacity for the email token bucket.
         *
         * @return Returns the email rate limit burst capacity.
         */
        public int getEmailBurst() {
            return emailBurst;
        }

        /**
         * Sets the burst capacity for the email token bucket.
         *
         * @param emailBurst Email burst capacity.
         * @return Updates the email burst capacity configuration.
         */
        public void setEmailBurst(int emailBurst) {
            this.emailBurst = emailBurst;
        }

        /**
         * Returns the allowed SMS sends per second.
         *
         * @return Returns the SMS rate limit per second.
         */
        public int getSmsPerSecond() {
            return smsPerSecond;
        }

        /**
         * Sets the allowed SMS sends per second.
         *
         * @param smsPerSecond SMS sends per second.
         * @return Updates the SMS rate limit configuration.
         */
        public void setSmsPerSecond(int smsPerSecond) {
            this.smsPerSecond = smsPerSecond;
        }

        /**
         * Returns the burst capacity for the SMS token bucket.
         *
         * @return Returns the SMS rate limit burst capacity.
         */
        public int getSmsBurst() {
            return smsBurst;
        }

        /**
         * Sets the burst capacity for the SMS token bucket.
         *
         * @param smsBurst SMS burst capacity.
         * @return Updates the SMS burst capacity configuration.
         */
        public void setSmsBurst(int smsBurst) {
            this.smsBurst = smsBurst;
        }

        /**
         * Returns the allowed push sends per second.
         *
         * @return Returns the push rate limit per second.
         */
        public int getPushPerSecond() {
            return pushPerSecond;
        }

        /**
         * Sets the allowed push sends per second.
         *
         * @param pushPerSecond Push sends per second.
         * @return Updates the push rate limit configuration.
         */
        public void setPushPerSecond(int pushPerSecond) {
            this.pushPerSecond = pushPerSecond;
        }

        /**
         * Returns the burst capacity for the push token bucket.
         *
         * @return Returns the push rate limit burst capacity.
         */
        public int getPushBurst() {
            return pushBurst;
        }

        /**
         * Sets the burst capacity for the push token bucket.
         *
         * @param pushBurst Push burst capacity.
         * @return Updates the push burst capacity configuration.
         */
        public void setPushBurst(int pushBurst) {
            this.pushBurst = pushBurst;
        }

        /**
         * Returns whether rate limiting should fail open when Redis is unavailable.
         *
         * @return Returns true when Redis failures should allow sends instead of throttling.
         */
        public boolean isFailOpenOnError() {
            return failOpenOnError;
        }

        /**
         * Sets whether rate limiting should fail open when Redis is unavailable.
         *
         * @param failOpenOnError Flag controlling fail-open behavior on Redis errors.
         * @return Updates the fail-open behavior configuration.
         */
        public void setFailOpenOnError(boolean failOpenOnError) {
            this.failOpenOnError = failOpenOnError;
        }
    }

    /**
     * Defines DLQ replay protection and operational enablement flags.
     */
    public static final class Dlq {
        @Min(1)
        @Max(1000)
        private int maxReplayAttempts = 5;

        private final Replay replay = new Replay();

        /**
         * Returns the maximum number of DLQ replay attempts allowed before dropping a record.
         *
         * @return Returns the maximum allowed replay attempts.
         */
        public int getMaxReplayAttempts() {
            return maxReplayAttempts;
        }

        /**
         * Sets the maximum number of DLQ replay attempts allowed before dropping a record.
         *
         * @param maxReplayAttempts Maximum allowed replay attempts.
         * @return Updates the DLQ replay guard configuration.
         */
        public void setMaxReplayAttempts(int maxReplayAttempts) {
            this.maxReplayAttempts = maxReplayAttempts;
        }

        /**
         * Returns the DLQ replay enablement settings.
         *
         * @return Returns the DLQ replay enablement settings.
         */
        public Replay getReplay() {
            return replay;
        }

        /**
         * Defines operational enablement controls for DLQ replay.
         */
        public static final class Replay {
            private boolean enabled = false;

            /**
             * Returns whether DLQ replay processing is enabled.
             *
             * @return Returns true when DLQ replay listeners should be started.
             */
            public boolean isEnabled() {
                return enabled;
            }

            /**
             * Enables or disables DLQ replay processing.
             *
             * @param enabled Flag indicating whether DLQ replay is enabled.
             * @return Updates DLQ replay enablement.
             */
            public void setEnabled(boolean enabled) {
                this.enabled = enabled;
            }
        }
    }

    /**
     * Defines global dispatch enablement used as an operational safety switch.
     */
    public static final class Dispatch {
        private boolean enabled = true;

        /**
         * Returns whether dispatch should be enabled for notification delivery workflows.
         *
         * @return Returns true when dispatch is enabled.
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Enables or disables dispatch for notification delivery workflows.
         *
         * @param enabled Flag controlling dispatch enablement.
         * @return Updates dispatch enablement configuration.
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Defines SMS channel enablement used as an operational safety switch.
     */
    public static final class Sms {
        private boolean enabled = true;

        /**
         * Returns whether the SMS channel is enabled.
         *
         * @return Returns true when the SMS channel is enabled.
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Enables or disables the SMS channel.
         *
         * @param enabled Flag controlling SMS channel enablement.
         * @return Updates SMS channel enablement configuration.
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Defines push channel enablement used as an operational safety switch.
     */
    public static final class Push {
        private boolean enabled = true;

        /**
         * Returns whether the push channel is enabled.
         *
         * @return Returns true when the push channel is enabled.
         */
        public boolean isEnabled() {
            return enabled;
        }

        /**
         * Enables or disables the push channel.
         *
         * @param enabled Flag controlling push channel enablement.
         * @return Updates push channel enablement configuration.
         */
        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }
    }

    /**
     * Defines tenant-aware template override settings and versioning defaults.
     */
    public static final class Templates {
        private String defaultTenantFolder = "default";
        private String defaultVersion = "v1";

        /**
         * Returns the default tenant template folder name.
         *
         * @return Returns the default tenant template folder name.
         */
        public String getDefaultTenantFolder() {
            return defaultTenantFolder;
        }

        /**
         * Sets the default tenant template folder name.
         *
         * @param defaultTenantFolder Default tenant template folder name.
         * @return Updates the default tenant template folder configuration.
         */
        public void setDefaultTenantFolder(String defaultTenantFolder) {
            this.defaultTenantFolder = defaultTenantFolder;
        }

        /**
         * Returns the default template version identifier.
         *
         * @return Returns the default version identifier.
         */
        public String getDefaultVersion() {
            return defaultVersion;
        }

        /**
         * Sets the default template version identifier.
         *
         * @param defaultVersion Default version identifier.
         * @return Updates the default version identifier.
         */
        public void setDefaultVersion(String defaultVersion) {
            this.defaultVersion = defaultVersion;
        }
    }
}

