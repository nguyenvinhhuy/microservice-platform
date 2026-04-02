package huynv.inventoryservice.service;

import huynv.inventoryservice.config.InventoryProperties;
import huynv.inventoryservice.domain.Inventory;
import huynv.inventoryservice.domain.InventoryReservation;
import huynv.inventoryservice.domain.InventoryReservationItem;
import huynv.inventoryservice.domain.ReservationStatus;
import huynv.inventoryservice.dto.ReservationItem;
import huynv.inventoryservice.dto.ReserveStockRequest;
import huynv.inventoryservice.exception.ConcurrentStockUpdateException;
import huynv.inventoryservice.exception.InsufficientStockException;
import huynv.inventoryservice.exception.InvalidReservationStatusException;
import huynv.inventoryservice.exception.ReservationNotFoundException;
import huynv.inventoryservice.exception.TenantOwnershipViolationException;
import huynv.inventoryservice.repository.InventoryRepository;
import huynv.inventoryservice.repository.InventoryReservationRepository;
import huynv.inventoryservice.security.UserContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository reservationRepository;
    private final InventoryProperties inventoryProperties;
    private final MeterRegistry meterRegistry;
    private final InventoryOutboxService inventoryOutboxService;

    /**
     * Creates an inventory service that performs tenant-scoped reservations and emits integration events.
     *
     * @param inventoryRepository The repository for inventory item rows.
     * @param reservationRepository The repository for reservation rows.
     * @param inventoryProperties The inventory configuration properties.
     * @param meterRegistry The meter registry used for inventory metrics.
     * @param inventoryOutboxService The outbox service used to persist integration events reliably.
     * @return Initializes an inventory service instance.
     */
    public InventoryService(InventoryRepository inventoryRepository,
                            InventoryReservationRepository reservationRepository,
                            InventoryProperties inventoryProperties,
                            MeterRegistry meterRegistry,
                            InventoryOutboxService inventoryOutboxService) {
        this.inventoryRepository = inventoryRepository;
        this.reservationRepository = reservationRepository;
        this.inventoryProperties = inventoryProperties;
        this.meterRegistry = meterRegistry;
        this.inventoryOutboxService = inventoryOutboxService;
    }

    /**
     * Reserves stock atomically for a given order and enqueues a StockReservedEvent via outbox.
     *
     * @param request The reservation request containing order id and item quantities.
     * @return Persists reservation state and enqueues an outbox event for downstream services.
     */
    @Transactional
    public void reserveStock(ReserveStockRequest request) {
        long startNanos = System.nanoTime();
        setupMdc(request.getOrderId());
        Long tenantId = UserContext.getTenantId();
        log.info("Attempting to reserve stock for orderId: {} and tenantId: {}", request.getOrderId(), tenantId);

        InventoryReservation existingReservation = reservationRepository
                .findByOrderIdAndTenantId(request.getOrderId(), tenantId)
                .orElse(null);
        if (existingReservation != null) {
            log.info(
                    "Idempotent reserve request for orderId {} and tenantId {}. Existing status={}",
                    request.getOrderId(),
                    tenantId,
                    existingReservation.getStatus()
            );
            return;
        }

        try {
            // Fetch all required inventory items in a single, tenant-aware query.
            List<Long> productIds = request.getItems().stream().map(ReservationItem::getProductId).toList();
            Map<Long, Inventory> inventoryMap = inventoryRepository.findAllByProductIdInAndTenantId(productIds, tenantId)
                    .stream().collect(Collectors.toMap(Inventory::getProductId, Function.identity()));
            ensureInventoryOwnership(productIds, inventoryMap, tenantId);

            // Create the reservation entity.
            InventoryReservation reservation = createReservationEntity(request, tenantId);

            // Atomically reserve stock for each item to prevent overselling under high concurrency.
            List<InventoryReservationItem> reservationItems = request.getItems().stream().map(item -> {
                MDC.put("productId", String.valueOf(item.getProductId()));
                try {
                    int updated = inventoryRepository.reserveStockIfAvailable(tenantId, item.getProductId(), item.getQuantity());
                    if (updated != 1) {
                        handleReservationFailure(
                                request.getOrderId(),
                                tenantId,
                                "insufficient_stock",
                                "Insufficient stock for product " + item.getProductId()
                        );
                        throw new InsufficientStockException("Insufficient stock for product " + item.getProductId());
                    }
                    return createReservationItemEntity(reservation, item);
                } finally {
                    MDC.remove("productId");
                }
            }).toList();

            reservation.setItems(reservationItems);

            // Persist reservation after all atomic stock updates succeed.
            reservationRepository.save(reservation);

            // Persist outbox event inside the same transaction to prevent lost Kafka messages on crashes.
            inventoryOutboxService.enqueueStockReserved(reservation, OffsetDateTime.now());
            for (Inventory inventory : inventoryRepository.findAllByProductIdInAndTenantId(productIds, tenantId)) {
                inventoryOutboxService.enqueueStockUpdated(inventory, OffsetDateTime.now(), reservation.getCorrelationId());
            }
            meterRegistry.counter("inventory.reservation", "status", "success").increment();
            log.info("Successfully reserved stock for orderId: {}", request.getOrderId());

        } catch (ObjectOptimisticLockingFailureException e) {
            handleReservationFailure(request.getOrderId(), tenantId, "optimistic_lock", "Concurrent stock update detected");
            throw new ConcurrentStockUpdateException("Concurrent stock update detected, please retry.", e);
        } finally {
            meterRegistry.timer("inventory_reservation_latency", "service", "inventory-service")
                    .record(System.nanoTime() - startNanos, java.util.concurrent.TimeUnit.NANOSECONDS);
            clearMdc();
        }
    }

    /**
     * Confirms an existing RESERVED reservation and finalizes stock by decrementing total and reserved counts.
     *
     * @param orderId Order identifier owning the reservation to confirm.
     * @return no return; persists CONFIRMED status and enqueues an outbox event for asynchronous Kafka publishing.
     */
    @Transactional
    public void confirmStock(UUID orderId) {
        setupMdc(orderId);
        Long tenantId = UserContext.getTenantId();
        log.info("Confirming stock for orderId: {} and tenantId: {}", orderId, tenantId);

        try {
            InventoryReservation reservation = findReservationByOrderIdAndTenantId(orderId, tenantId);

            // Idempotency: If already confirmed, do nothing and return success.
            if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
                log.warn("Reservation for orderId {} is already confirmed. (Idempotent)", orderId);
                return;
            }

            // State validation: Only RESERVED stock can be confirmed.
            if (reservation.getStatus() != ReservationStatus.RESERVED) {
                handleStateChangeFailure(orderId, tenantId, "confirm", "invalid_state");
                throw new InvalidReservationStatusException("Cannot confirm reservation with status " + reservation.getStatus());
            }

            List<Long> productIds = reservation.getItems().stream().map(InventoryReservationItem::getProductId).toList();
            Map<Long, Inventory> inventoryMap = inventoryRepository.findAllByProductIdInAndTenantId(productIds, tenantId)
                    .stream().collect(Collectors.toMap(Inventory::getProductId, Function.identity()));
            ensureInventoryOwnership(productIds, inventoryMap, tenantId);

            // Finalize the stock: decrease total and reserved stock as items are now "sold".
            for (InventoryReservationItem item : reservation.getItems()) {
                MDC.put("productId", String.valueOf(item.getProductId()));
                try {
                    Inventory inventory = inventoryMap.get(item.getProductId());
                    inventory.setTotalStock(inventory.getTotalStock() - item.getQuantity());
                    inventory.setReservedStock(inventory.getReservedStock() - item.getQuantity());
                } finally {
                    MDC.remove("productId");
                }
            }

            reservation.setStatus(ReservationStatus.CONFIRMED);
            inventoryRepository.saveAll(inventoryMap.values());

            inventoryOutboxService.enqueueStockConfirmed(reservation, OffsetDateTime.now());
            for (Inventory inventory : inventoryMap.values()) {
                inventoryOutboxService.enqueueStockUpdated(inventory, OffsetDateTime.now(), reservation.getCorrelationId());
            }
            meterRegistry.counter("inventory.state.change", "operation", "confirm", "status", "success").increment();
            log.info("Successfully confirmed stock for orderId: {}", orderId);

        } catch (ObjectOptimisticLockingFailureException e) {
            handleStateChangeFailure(orderId, tenantId, "confirm", "optimistic_lock");
            throw new ConcurrentStockUpdateException("Concurrent stock update detected on confirm, please retry.", e);
        } finally {
            clearMdc();
        }
    }

    /**
     * Releases an existing RESERVED reservation and restores stock availability by decrementing reserved counts.
     *
     * @param orderId Order identifier owning the reservation to release.
     * @return no return; persists RELEASED status and enqueues an outbox event for asynchronous Kafka publishing.
     */
    @Transactional
    public void releaseStock(UUID orderId) {
        setupMdc(orderId);
        Long tenantId = UserContext.getTenantId();
        log.info("Releasing stock for orderId: {} and tenantId: {}", orderId, tenantId);

        try {
            InventoryReservation reservation = findReservationByOrderIdAndTenantId(orderId, tenantId);

            // Idempotency: If already released or confirmed, do nothing.
            if (reservation.getStatus() == ReservationStatus.RELEASED) {
                log.warn("Reservation for orderId {} is already released. (Idempotent)", orderId);
                return;
            }
            if (reservation.getStatus() == ReservationStatus.CONFIRMED) {
                log.warn("Cannot release a CONFIRMED reservation for orderId {}.", orderId);
                // This is not an error, the business process has moved on.
                return;
            }

            // State validation: Only RESERVED stock can be released.
            if (reservation.getStatus() != ReservationStatus.RESERVED) {
                handleStateChangeFailure(orderId, tenantId, "release", "invalid_state");
                throw new InvalidReservationStatusException("Cannot release reservation with status " + reservation.getStatus());
            }

            releaseReservationLogic(reservation);
            meterRegistry.counter("inventory.state.change", "operation", "release", "status", "success").increment();
            log.info("Successfully released stock for orderId: {}", orderId);

        } catch (ObjectOptimisticLockingFailureException e) {
            handleStateChangeFailure(orderId, tenantId, "release", "optimistic_lock");
            throw new ConcurrentStockUpdateException("Concurrent stock update detected on release, please retry.", e);
        } finally {
            clearMdc();
        }
    }

    /**
     * Releases expired RESERVED reservations to prevent indefinite stock holds.
     *
     * @return no return; releases all expired reservations and enqueues outbox release events for Kafka publishing.
     */
    @Transactional
    public void releaseExpiredReservations() {
        log.info("Starting scheduled job to release expired reservations.");
        List<InventoryReservation> expiredReservations = reservationRepository
                .findByStatusAndExpiresAtBeforeAndTenantIdIsNotNull(ReservationStatus.RESERVED, OffsetDateTime.now());

        if (expiredReservations.isEmpty()) {
            log.info("No expired reservations found.");
            return;
        }

        log.info("Found {} expired reservations to release.", expiredReservations.size());
        for (InventoryReservation reservation : expiredReservations) {
            // Process each expired reservation in its own transaction context to isolate failures.
            try {
                setupMdc(reservation.getOrderId());
                log.info("Releasing expired reservation for orderId: {}", reservation.getOrderId());
                releaseReservationLogic(reservation);
                meterRegistry.counter("inventory.state.change", "operation", "release_expired", "status", "success").increment();
            } catch (Exception e) {
                // Log error but continue processing other expired reservations
                log.error("Failed to release expired reservation for orderId: {}", reservation.getOrderId(), e);
                handleStateChangeFailure(reservation.getOrderId(), reservation.getTenantId(), "release_expired", "job_error");
            } finally {
                clearMdc();
            }
        }
        log.info("Finished scheduled job to release expired reservations.");
    }

    /**
     * Applies stock release mutations for one reservation by decrementing reserved counts for all items.
     *
     * @param reservation Reservation entity whose reserved stock is being released.
     * @return no return; updates inventory rows and reservation status and schedules an internal release event.
     */
    private void releaseReservationLogic(InventoryReservation reservation) {
        List<Long> productIds = reservation.getItems().stream().map(InventoryReservationItem::getProductId).toList();
        Map<Long, Inventory> inventoryMap = inventoryRepository.findAllByProductIdInAndTenantId(productIds, reservation.getTenantId())
                .stream().collect(Collectors.toMap(Inventory::getProductId, Function.identity()));
        ensureInventoryOwnership(productIds, inventoryMap, reservation.getTenantId());

        for (InventoryReservationItem item : reservation.getItems()) {
            MDC.put("productId", String.valueOf(item.getProductId()));
            try {
                Inventory inventory = inventoryMap.get(item.getProductId());
                // Restore available stock by decreasing the reserved count.
                inventory.setReservedStock(inventory.getReservedStock() - item.getQuantity());
            } finally {
                MDC.remove("productId");
            }
        }

        reservation.setStatus(ReservationStatus.RELEASED);
        inventoryRepository.saveAll(inventoryMap.values());

        inventoryOutboxService.enqueueStockReleased(reservation, OffsetDateTime.now());
        for (Inventory inventory : inventoryMap.values()) {
            inventoryOutboxService.enqueueStockUpdated(inventory, OffsetDateTime.now(), reservation.getCorrelationId());
        }
    }

    /**
     * Loads one tenant-owned reservation by order id and prevents cross-tenant access by default.
     *
     * @param orderId Order identifier associated with the reservation.
     * @param tenantId Tenant scope used to enforce ownership.
     * @return Returns the existing reservation for the given tenant and order id.
     */
    private InventoryReservation findReservationByOrderIdAndTenantId(UUID orderId, Long tenantId) {
        return reservationRepository.findByOrderIdAndTenantId(orderId, tenantId)
                .orElseThrow(() -> new ReservationNotFoundException("Reservation for order " + orderId + " not found for this tenant."));
    }

    /**
     * Creates a new reservation entity in RESERVED status with a deterministic expiration time.
     *
     * @param request Incoming reservation request containing order id and payment metadata.
     * @param tenantId Tenant scope used to persist multi-tenant ownership.
     * @return Returns a new reservation entity to be persisted in the current transaction.
     */
    private InventoryReservation createReservationEntity(ReserveStockRequest request, Long tenantId) {
        return InventoryReservation.builder()
                .reservationId(UUID.randomUUID())
                .orderId(request.getOrderId())
                .tenantId(tenantId)
                .status(ReservationStatus.RESERVED)
                .expiresAt(OffsetDateTime.now().plus(inventoryProperties.getReservation().getExpiration()))
                .amount(request.getAmount())
                .currency(request.getCurrency())
                .paymentProvider(request.getPaymentProvider())
                .idempotencyKey(request.getIdempotencyKey())
                .correlationId(request.getCorrelationId())
                .traceId(request.getTraceId())
                .build();
    }

    /**
     * Creates a reservation item entity linked to a parent reservation.
     *
     * @param reservation Parent reservation entity owning the item.
     * @param item Requested product quantity to reserve.
     * @return Returns a new reservation item entity for persistence.
     */
    private InventoryReservationItem createReservationItemEntity(InventoryReservation reservation, ReservationItem item) {
        return InventoryReservationItem.builder()
                .reservation(reservation)
                .productId(item.getProductId())
                .quantity(item.getQuantity())
                .build();
    }

    /**
     * Records reservation failure metrics and emits an internal failure event for diagnostics and compensation.
     *
     * @param orderId Order identifier associated with the failed reservation.
     * @param tenantId Tenant scope owning the reservation attempt.
     * @param reason Failure reason key used for metrics labeling.
     * @param logMessage Human-readable failure message used for logs and event payloads.
     * @return no return; records failure metrics and emits an error log line.
     */
    private void handleReservationFailure(UUID orderId, Long tenantId, String reason, String logMessage) {
        log.error(logMessage);
        meterRegistry.counter("inventory.reservation", "status", "failed", "reason", reason).increment();
    }

    /**
     * Records failure metrics for a confirm or release state transition.
     *
     * @param orderId Order identifier whose state change failed.
     * @param tenantId Tenant scope for metrics labeling and diagnostics.
     * @param operation Operation name such as confirm or release.
     * @param reason Reason key used for metrics labeling.
     * @return no return; increments failure metrics and emits an error log line.
     */
    private void handleStateChangeFailure(UUID orderId, Long tenantId, String operation, String reason) {
        log.error("Failed to {} stock for orderId {}: {}", operation, orderId, reason);
        meterRegistry.counter("inventory.state.change", "operation", operation, "status", "failed", "reason", reason).increment();
    }

    /**
     * Populates MDC fields for consistent log correlation during one inventory operation.
     *
     * @param orderId Order identifier associated with the current request when available.
     * @return no return; sets MDC keys for tenantId, userId, and orderId when available.
     */
    private void setupMdc(UUID orderId) {
        Long tenantId = UserContext.getTenantId();
        Long userId = UserContext.getUserId();
        if (tenantId != null) {
            MDC.put("tenantId", String.valueOf(tenantId));
        }
        if (userId != null) {
            MDC.put("userId", String.valueOf(userId));
        }
        if (orderId != null) {
            MDC.put("orderId", orderId.toString());
        }
    }

    /**
     * Validates every requested product id is owned by the current tenant and was loaded successfully.
     *
     * @param productIds Product identifiers requested by the operation.
     * @param inventoryMap Loaded inventory rows keyed by product id.
     * @param tenantId Tenant scope used for exception messages and diagnostics.
     * @return no return; throws TenantOwnershipViolationException when any product id is missing.
     */
    private void ensureInventoryOwnership(List<Long> productIds, Map<Long, Inventory> inventoryMap, Long tenantId) {
        Set<Long> missing = new HashSet<>(productIds);
        missing.removeAll(inventoryMap.keySet());
        if (!missing.isEmpty()) {
            throw new TenantOwnershipViolationException(
                    "Inventory ownership validation failed for tenantId " + tenantId + ", productIds " + missing
            );
        }
    }

    /**
     * Clears MDC keys set for inventory operations to avoid context leakage across threads.
     *
     * @return no return; removes MDC keys used for tenantId, userId, orderId, and productId.
     */
    private void clearMdc() {
        MDC.remove("tenantId");
        MDC.remove("userId");
        MDC.remove("orderId");
        MDC.remove("productId");
    }
}
