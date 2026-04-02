package huynv.inventoryservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import huynv.event.schema.JsonSchemaValidationService;
import huynv.event.BaseEvent;
import huynv.event.EventFactory;
import huynv.event.inventory.StockConfirmedEvent;
import huynv.event.inventory.StockItem;
import huynv.event.inventory.StockReleasedEvent;
import huynv.event.inventory.StockReservedEvent;
import huynv.event.inventory.StockUpdatedEvent;
import huynv.inventoryservice.domain.InventoryReservation;
import huynv.inventoryservice.domain.Inventory;
import huynv.inventoryservice.domain.OutboxEvent;
import huynv.inventoryservice.domain.OutboxStatus;
import huynv.inventoryservice.domain.InventoryReservationItem;
import huynv.inventoryservice.repository.OutboxEventRepository;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Enqueues committed inventory integration events into an outbox table.
 */
@Service
public class InventoryOutboxService {

    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final EventFactory eventFactory;
    private final JsonSchemaValidationService schemaValidationService;

    /**
     * Creates an outbox enqueue service for inventory integration events.
     *
     * @param outboxEventRepository repository used to persist outbox rows.
     * @param objectMapper object mapper used to serialize payloads.
     * @param eventFactory event factory used to build unified event envelopes.
     * @param schemaValidationService Schema validation service used to validate and register event schemas.
     * @return Initializes the inventory outbox enqueue service.
     */
    public InventoryOutboxService(OutboxEventRepository outboxEventRepository,
                                  ObjectMapper objectMapper,
                                  EventFactory eventFactory,
                                  JsonSchemaValidationService schemaValidationService) {
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.eventFactory = eventFactory;
        this.schemaValidationService = schemaValidationService;
    }

    /**
     * Enqueues a canonical StockReservedEvent in the same database transaction as the reservation mutation.
     *
     * @param reservation inventory reservation snapshot that has been persisted in the current transaction.
     * @param now current timestamp used for event metadata and outbox scheduling.
     * @return persists a PENDING outbox row that will be published asynchronously.
     */
    public void enqueueStockReserved(InventoryReservation reservation, OffsetDateTime now) {
        List<StockReservedEvent.ReservedItem> items = reservation.getItems().stream()
                .map(this::toReservedItem)
                .toList();

        StockReservedEvent data = new StockReservedEvent(
                reservation.getOrderId(),
                reservation.getTenantId(),
                reservation.getAmount(),
                reservation.getCurrency(),
                reservation.getPaymentProvider(),
                reservation.getIdempotencyKey(),
                items
        );

        BaseEvent<StockReservedEvent> event = eventFactory.create(
                "inventory.stock.reserved",
                "order-" + reservation.getOrderId(),
                0L,
                "inventory.stock.reserved.v1",
                reservation.getCorrelationId(),
                null,
                data
        );

        outboxEventRepository.save(OutboxEvent.builder()
                .eventType(event.eventType())
                .partitionKey(reservation.getOrderId().toString())
                .payload(toJson(event))
                .status(OutboxStatus.PENDING)
                .correlationId(event.correlationId())
                .traceId(event.traceId())
                .retryCount(0)
                .nextAttemptAt(now)
                .build());
    }

    /**
     * Enqueues a StockConfirmed event in the same database transaction as the confirmation mutation.
     *
     * @param reservation inventory reservation snapshot that has been updated to CONFIRMED in the current transaction.
     * @param now current timestamp used for event metadata and outbox scheduling.
     * @return persists a PENDING outbox row that will be published asynchronously.
     */
    public void enqueueStockConfirmed(InventoryReservation reservation, OffsetDateTime now) {
        List<StockItem> items = reservation.getItems().stream()
                .map(this::toStockItem)
                .toList();

        StockConfirmedEvent data = new StockConfirmedEvent(reservation.getOrderId(), reservation.getTenantId(), items);

        BaseEvent<StockConfirmedEvent> event = eventFactory.create(
                "inventory.stock.confirmed",
                "order-" + reservation.getOrderId(),
                0L,
                "inventory.stock.confirmed.v1",
                reservation.getCorrelationId(),
                null,
                data
        );

        outboxEventRepository.save(OutboxEvent.builder()
                .eventType(event.eventType())
                .partitionKey(reservation.getOrderId().toString())
                .payload(toJson(event))
                .status(OutboxStatus.PENDING)
                .correlationId(event.correlationId())
                .traceId(event.traceId())
                .retryCount(0)
                .nextAttemptAt(now)
                .build());
    }

    /**
     * Enqueues a StockReleased event in the same database transaction as the release mutation.
     *
     * @param reservation inventory reservation snapshot that has been updated to RELEASED in the current transaction.
     * @param now current timestamp used for event metadata and outbox scheduling.
     * @return persists a PENDING outbox row that will be published asynchronously.
     */
    public void enqueueStockReleased(InventoryReservation reservation, OffsetDateTime now) {
        List<StockItem> items = reservation.getItems().stream()
                .map(this::toStockItem)
                .toList();

        StockReleasedEvent data = new StockReleasedEvent(reservation.getOrderId(), reservation.getTenantId(), items);

        BaseEvent<StockReleasedEvent> event = eventFactory.create(
                "inventory.stock.released",
                "order-" + reservation.getOrderId(),
                0L,
                "inventory.stock.released.v1",
                reservation.getCorrelationId(),
                null,
                data
        );

        outboxEventRepository.save(OutboxEvent.builder()
                .eventType(event.eventType())
                .partitionKey(reservation.getOrderId().toString())
                .payload(toJson(event))
                .status(OutboxStatus.PENDING)
                .correlationId(event.correlationId())
                .traceId(event.traceId())
                .retryCount(0)
                .nextAttemptAt(now)
                .build());
    }

    /**
     * Enqueues an inventory stock snapshot update event for one product to drive read model projections.
     *
     * @param inventory Inventory row that has been mutated in the current transaction.
     * @param now Current timestamp used for outbox scheduling.
     * @param correlationId Correlation identifier used to tie updates to a larger business flow.
     * @return Persists a PENDING outbox row that will be published asynchronously.
     */
    public void enqueueStockUpdated(Inventory inventory, OffsetDateTime now, String correlationId) {
        StockUpdatedEvent data = new StockUpdatedEvent(
                inventory.getTenantId(),
                inventory.getProductId(),
                inventory.getTotalStock(),
                inventory.getReservedStock(),
                inventory.getAvailableStock()
        );

        BaseEvent<StockUpdatedEvent> event = eventFactory.create(
                "inventory.stock.updated",
                "product-" + inventory.getProductId(),
                inventory.getVersion() == null ? 0L : inventory.getVersion(),
                "inventory.stock.updated.v1",
                correlationId,
                null,
                data
        );

        outboxEventRepository.save(OutboxEvent.builder()
                .eventType(event.eventType())
                .partitionKey(String.valueOf(inventory.getProductId()))
                .payload(toJson(event))
                .status(OutboxStatus.PENDING)
                .correlationId(event.correlationId())
                .traceId(event.traceId())
                .retryCount(0)
                .nextAttemptAt(now)
                .build());
    }

    /**
     * Maps a reservation item entity to a canonical reserved item payload.
     *
     * @param item persisted reservation item entity.
     * @return returns reserved item payload containing product id and quantity.
     */
    private StockReservedEvent.ReservedItem toReservedItem(InventoryReservationItem item) {
        return new StockReservedEvent.ReservedItem(item.getProductId(), item.getQuantity());
    }

    /**
     * Maps a reservation item entity to a canonical stock item payload.
     *
     * @param item persisted reservation item entity.
     * @return returns stock item payload containing product id and quantity.
     */
    private StockItem toStockItem(InventoryReservationItem item) {
        return new StockItem(item.getProductId(), item.getQuantity());
    }

    /**
     * Serializes a payload object to JSON for immutable outbox storage.
     *
     * @param payload payload object to serialize.
     * @return returns JSON string.
     */
    private String toJson(Object payload) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            if (payload instanceof BaseEvent<?> envelope && envelope.dataSchema() != null && !envelope.dataSchema().isBlank()) {
                schemaValidationService.validateAndRegister(envelope.dataSchema(), json);
            }
            return json;
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to serialize inventory outbox payload.", ex);
        }
    }
}


