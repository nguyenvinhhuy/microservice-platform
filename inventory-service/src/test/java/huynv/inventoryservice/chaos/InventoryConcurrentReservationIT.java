package huynv.inventoryservice.chaos;

import huynv.inventoryservice.domain.Inventory;
import huynv.inventoryservice.dto.ReserveStockRequest;
import huynv.inventoryservice.dto.ReservationItem;
import huynv.inventoryservice.exception.InsufficientStockException;
import huynv.inventoryservice.repository.InventoryRepository;
import huynv.inventoryservice.security.UserContext;
import huynv.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Validates inventory reservation concurrency safety using a real PostgreSQL database.
 */
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class InventoryConcurrentReservationIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private InventoryRepository inventoryRepository;

    @Autowired
    private InventoryService inventoryService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void setUp() {
        UserContext.setTenantId(1L);
        UserContext.setUserId(1L);
        inventoryRepository.deleteAll();

        Inventory inventory = new Inventory();
        inventory.setTenantId(1L);
        inventory.setProductId(1L);
        inventory.setTotalStock(10);
        inventory.setReservedStock(0);
        inventory.setVersion(0L);
        inventoryRepository.save(inventory);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    void shouldPreventOversellingWithAtomicReserveUpdate() throws Exception {
        ReserveStockRequest first = request(UUID.randomUUID(), 1L, 7);
        ReserveStockRequest second = request(UUID.randomUUID(), 1L, 7);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Callable<Void> firstCall = () -> {
                inventoryService.reserveStock(first);
                return null;
            };
            Callable<Void> secondCall = () -> {
                inventoryService.reserveStock(second);
                return null;
            };
            Future<Void> f1 = executor.submit(firstCall);
            Future<Void> f2 = executor.submit(secondCall);

            int failures = 0;
            failures += countFailure(f1);
            failures += countFailure(f2);
            assertEquals(1, failures);

            Inventory row = inventoryRepository.findAll().getFirst();
            assertEquals(10, row.getTotalStock());
            assertEquals(7, row.getReservedStock());
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Simulates lock contention on the inventory row and verifies reservation completes after the lock is released.
     *
     * @return No return; asserts the reservation call completes within a bounded time window.
     */
    @Test
    void shouldHandleDatabaseLockContentionWithoutDeadlock() throws Exception {
        ReserveStockRequest request = request(UUID.randomUUID(), 1L, 1);

        CountDownLatch locked = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Void> lockHolder = executor.submit(() -> {
                TransactionTemplate tx = new TransactionTemplate(transactionManager);
                tx.executeWithoutResult(status -> {
                    jdbcTemplate.queryForObject(
                            "select id from inventory where tenant_id = 1 and product_id = 1 for update",
                            Long.class
                    );
                    locked.countDown();
                    try {
                        Thread.sleep(1500);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                    }
                });
                return null;
            });

            assertTrue(locked.await(5, TimeUnit.SECONDS));

            long startedAt = System.nanoTime();
            inventoryService.reserveStock(request);
            Duration duration = Duration.ofNanos(System.nanoTime() - startedAt);

            lockHolder.get(10, TimeUnit.SECONDS);

            assertTrue(duration.toMillis() >= 1000);
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Creates a minimal reserve stock request for a single item.
     *
     * @param orderId Order identifier used for reservation idempotency.
     * @param productId Product identifier to reserve.
     * @param quantity Quantity to reserve.
     * @return Returns a ReserveStockRequest populated with one item.
     */
    private static ReserveStockRequest request(UUID orderId, long productId, int quantity) {
        ReservationItem item = new ReservationItem();
        item.setProductId(productId);
        item.setQuantity(quantity);
        ReserveStockRequest request = new ReserveStockRequest();
        request.setOrderId(orderId);
        request.setItems(List.of(item));
        return request;
    }

    /**
     * Counts whether a future failed with InsufficientStockException.
     *
     * @param future Future to evaluate.
     * @return Returns 1 when the future failed with InsufficientStockException and 0 otherwise.
     */
    private static int countFailure(Future<Void> future) throws Exception {
        try {
            future.get();
            return 0;
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            assertThrows(InsufficientStockException.class, () -> {
                throw cause;
            });
            return 1;
        }
    }
}
