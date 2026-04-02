package huynv.inventoryservice.service;

import huynv.inventoryservice.config.InventoryProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled task to automatically release expired stock reservations.
 * Uses ShedLock to ensure the task runs on only one service instance in a distributed environment.
 */
@Component
@ConditionalOnProperty(name = "shedlock.enabled", havingValue = "true", matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class ReservationExpirationScheduler {

    private final InventoryService inventoryService;
    private final InventoryProperties inventoryProperties;

    /**
     * Periodically scans for and releases expired stock reservations.
     * The schedule is controlled by 'inventory.reservation.expiration-check-interval'.
     * The @SchedulerLock annotation prevents concurrent execution across multiple pods,
     * making the job safe for distributed systems.
     */
    @Scheduled(fixedDelayString = "${inventory.reservation.expiration-check-interval}")
    @SchedulerLock(name = "releaseExpiredReservations", lockAtLeastFor = "PT30S", lockAtMostFor = "PT5M")
    public void releaseExpiredReservations() {
        log.info("Scheduler starting: release expired reservations.");

        try {
            inventoryService.releaseExpiredReservations();
            log.info("Scheduler finished: successfully released expired reservations.");
        } catch (Exception e) {
            // Catching all exceptions to ensure the scheduler doesn't die.
            // TODO: Consider specific exception handling and retry mechanism
            log.error("Error during scheduled release of expired reservations.", e);
        }
    }
}
