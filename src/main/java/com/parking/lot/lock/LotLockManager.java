package com.parking.lot.lock;

import com.parking.lot.config.ParkingProperties;
import com.parking.lot.web.error.ApiException;
import com.parking.lot.web.error.ErrorCode;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Layer 2 of the concurrency design (architecture §7): JVM-level lock striping.
 *
 * <ul>
 *   <li><b>Check-in</b> serialises per <em>floor</em> so a spot can never be handed to two gates.</li>
 *   <li><b>Check-out</b> serialises per <em>ticket</em> so a receipt is issued exactly once.</li>
 *   <li>The two paths never take each other's lock (entry: floor only, exit: ticket only), so
 *       they cannot deadlock with one another.</li>
 *   <li>{@code tryLock} + timeout degrades a saturated gate to {@code 503 GATE_BUSY} instead of
 *       piling up threads.</li>
 * </ul>
 *
 * <p><b>Release semantics:</b> when a transaction is active the lock is released in
 * {@code afterCompletion}, i.e. <em>after commit or rollback</em>. Releasing it as soon as the
 * service method body ends would let a second gate read the still-uncommitted FREE status and
 * double-book the spot.
 */
@Component
public class LotLockManager {

    /** Fixed stripe pools - bounded memory regardless of how many floors/tickets exist. */
    private static final int FLOOR_STRIPES = 64;
    private static final int TICKET_STRIPES = 128;

    private final ReentrantLock[] floorLocks = new ReentrantLock[FLOOR_STRIPES];
    private final ReentrantLock[] ticketLocks = new ReentrantLock[TICKET_STRIPES];
    private final long timeoutMs;

    public LotLockManager(ParkingProperties properties) {
        this.timeoutMs = properties.getLockTimeoutMs();
        for (int i = 0; i < floorLocks.length; i++) {
            floorLocks[i] = new ReentrantLock();
        }
        for (int i = 0; i < ticketLocks.length; i++) {
            ticketLocks[i] = new ReentrantLock();
        }
    }

    public <T> T withFloorLock(int floorNo, Supplier<T> work) {
        return with(floorLocks[Math.floorMod(floorNo, FLOOR_STRIPES)], "floor-" + floorNo, work);
    }

    /**
     * Keyed by ticket <em>number</em> rather than id: the caller must be able to lock before it
     * has read the ticket row, otherwise it would hold a pre-lock snapshot of the entity and the
     * {@code @Version} check would fire instead of the idempotency check (threat R4).
     */
    public <T> T withTicketLock(String ticketNo, Supplier<T> work) {
        int stripe = Math.floorMod(ticketNo == null ? 0 : ticketNo.hashCode(), TICKET_STRIPES);
        return with(ticketLocks[stripe], "ticket-" + ticketNo, work);
    }

    private <T> T with(ReentrantLock lock, String name, Supplier<T> work) {
        boolean acquired;
        try {
            acquired = lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException(ErrorCode.GATE_BUSY, "Interrupted while waiting for " + name, e);
        }
        if (!acquired) {
            throw new ApiException(ErrorCode.GATE_BUSY, "Gate busy: contended for " + name);
        }

        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            final boolean outermost = lock.getHoldCount() == 1;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (outermost) {
                        lock.unlock();
                    }
                }
            });
            // Deliberately no unlock here - see class javadoc.
            return work.get();
        }

        try {
            return work.get();
        } finally {
            lock.unlock();
        }
    }
}
