package huynv.paymentservice.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.event.schema.JsonSchemaValidationService;
import huynv.event.BaseEvent;
import huynv.event.EventFactory;
import huynv.event.payment.PaymentCompletedEvent;
import huynv.event.payment.PaymentFailedEvent;
import huynv.event.payment.PaymentProcessingEvent;
import huynv.paymentservice.domain.PaymentOutbox;
import huynv.paymentservice.exception.PaymentDomainException;
import huynv.paymentservice.repository.PaymentOutboxRepository;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import huynv.paymentservice.domain.Payment;

/**
 * Enqueues payment domain events into the outbox table for reliable publishing.
 */
@Component
public class PaymentEventProducer {

    private final PaymentOutboxRepository paymentOutboxRepository;
    private final ObjectMapper objectMapper;
    private final EventFactory eventFactory;
    private final JsonSchemaValidationService schemaValidationService;

    /**
     * Creates an outbox-backed event producer for the payment domain.
     *
     * @param paymentOutboxRepository Repository used to persist outbox records.
     * @param objectMapper ObjectMapper used to serialize event payloads as JSON.
     * @param eventFactory Event factory used to build unified event envelopes.
     * @param schemaValidationService Schema validation service used to validate and register event schemas.
     * @return Initializes an outbox-backed payment event producer.
     */
    public PaymentEventProducer(PaymentOutboxRepository paymentOutboxRepository,
                                ObjectMapper objectMapper,
                                EventFactory eventFactory,
                                JsonSchemaValidationService schemaValidationService) {
        this.paymentOutboxRepository = paymentOutboxRepository;
        this.objectMapper = objectMapper;
        this.eventFactory = eventFactory;
        this.schemaValidationService = schemaValidationService;
    }

    /**
     * Enqueues a PaymentProcessingEvent for the given payment aggregate.
     *
     * @param payment Payment aggregate whose processing has started.
     * @param now Timestamp for the event.
     * @return Persists a new unpublished outbox record for payment processing.
     */
    public void enqueueProcessing(Payment payment, OffsetDateTime now) {
        PaymentProcessingEvent data = new PaymentProcessingEvent(payment.getOrderId(), payment.getPaymentId(), payment.getTenantId());
        BaseEvent<PaymentProcessingEvent> envelope = eventFactory.create(
                "payment.processing",
                "payment-" + payment.getPaymentId(),
                0L,
                "payment.processing.v1",
                payment.getCorrelationId(),
                null,
                data
        );
        saveOutbox(payment, envelope.eventType(), envelope);
    }

    /**
     * Enqueues a PaymentSucceededEvent for the given payment aggregate.
     *
     * @param payment Payment aggregate that has succeeded.
     * @param now Timestamp for the event.
     * @return Persists a new unpublished outbox record for payment success.
     */
    public void enqueueSucceeded(Payment payment, OffsetDateTime now) {
        PaymentCompletedEvent data = new PaymentCompletedEvent(
                payment.getOrderId(),
                payment.getPaymentId(),
                payment.getTenantId(),
                payment.getTransactionId()
        );
        BaseEvent<PaymentCompletedEvent> envelope = eventFactory.create(
                "payment.completed",
                "payment-" + payment.getPaymentId(),
                0L,
                "payment.completed.v1",
                payment.getCorrelationId(),
                null,
                data
        );
        saveOutbox(payment, envelope.eventType(), envelope);
    }

    /**
     * Enqueues a PaymentFailedEvent for the given payment aggregate.
     *
     * @param payment Payment aggregate that has failed.
     * @param reason Failure reason for diagnostics and consumer behavior.
     * @param now Timestamp for the event.
     * @return Persists a new unpublished outbox record for payment failure.
     */
    public void enqueueFailed(Payment payment, String reason, OffsetDateTime now) {
        PaymentFailedEvent data = new PaymentFailedEvent(payment.getOrderId(), payment.getPaymentId(), payment.getTenantId(), reason);
        BaseEvent<PaymentFailedEvent> envelope = eventFactory.create(
                "payment.failed",
                "payment-" + payment.getPaymentId(),
                0L,
                "payment.failed.v1",
                payment.getCorrelationId(),
                null,
                data
        );
        saveOutbox(payment, envelope.eventType(), envelope);
    }

    /**
     * Serializes an event object and stores it as an unpublished outbox record.
     *
     * @param payment Payment aggregate used for aggregate routing and correlation.
     * @param eventType Event type name used by consumers for routing.
     * @param event Event payload to serialize.
     * @return Persists an unpublished outbox record for later Kafka publishing.
     */
    private void saveOutbox(Payment payment, String eventType, Object event) {
        try {
            String payload = objectMapper.writeValueAsString(event);
            if (event instanceof BaseEvent<?> envelope && envelope.dataSchema() != null && !envelope.dataSchema().isBlank()) {
                schemaValidationService.validateAndRegister(envelope.dataSchema(), payload);
            }
            PaymentOutbox outbox = PaymentOutbox.unpublished(
                    "Payment",
                    payment.getPaymentId().toString(),
                    eventType,
                    payload,
                    payment.getCorrelationId(),
                    payment.getTraceId(),
                    null,
                    OffsetDateTime.now()
            );
            paymentOutboxRepository.save(outbox);
        } catch (JsonProcessingException e) {
            throw new PaymentDomainException("Failed to serialize outbox payload for eventType=" + eventType + ".", e);
        }
    }
}


