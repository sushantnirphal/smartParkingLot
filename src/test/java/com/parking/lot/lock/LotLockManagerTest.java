package com.parking.lot.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.parking.lot.config.ParkingProperties;
import com.parking.lot.web.error.ApiException;
import com.parking.lot.web.error.ErrorCode;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Task C4 - lock manager unit suite (architecture §7 layer 2).
 * Runs without Spring or a transaction, so the lock is released in a finally block.
 */
class LotLockManagerTest {

    private static LotLockManager manager(long timeoutMs) {
        ParkingProperties properties = new ParkingProperties();
        properties.setLockTimeoutMs(timeoutMs);
        return new LotLockManager(properties);
    }

    @Test
    @DisplayName("a floor lock serialises every thread on that floor")
    void sameFloorIsSerialised() throws Exception {
        LotLockManager locks = manager(5000);
        AtomicInteger concurrent = new AtomicInteger();
        AtomicInteger maxConcurrent = new AtomicInteger();
        AtomicInteger counter = new AtomicInteger();

        runConcurrently(8, () -> locks.withFloorLock(1, () -> {
            int now = concurrent.incrementAndGet();
            maxConcurrent.accumulateAndGet(now, Math::max);
            counter.incrementAndGet();
            sleep(10);
            concurrent.decrementAndGet();
            return null;
        }));

        assertThat(maxConcurrent.get()).isEqualTo(1);
        assertThat(counter.get()).isEqualTo(8);
    }

    @Test
    @DisplayName("different floors run in parallel (striping gives N-way concurrency)")
    void differentFloorsRunInParallel() throws Exception {
        LotLockManager locks = manager(5000);
        CountDownLatch bothInside = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);

        try {
            for (int floor = 1; floor <= 2; floor++) {
                final int floorNo = floor;
                pool.submit(() -> locks.withFloorLock(floorNo, () -> {
                    bothInside.countDown();
                    try {
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                }));
            }
            // Both threads must be able to hold their floor lock at the same time.
            assertThat(bothInside.await(5, TimeUnit.SECONDS)).isTrue();
            release.countDown();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    @DisplayName("contention degrades to 503 GATE_BUSY instead of piling up threads")
    void timeoutRaisesGateBusy() throws Exception {
        LotLockManager locks = manager(100);
        CountDownLatch inside = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicReference<Throwable> observed = new AtomicReference<Throwable>();
        CountDownLatch done = new CountDownLatch(1);

        Thread holder = new Thread(() -> locks.withFloorLock(3, () -> {
            inside.countDown();
            await(release);
            return null;
        }));
        holder.start();
        assertThat(inside.await(5, TimeUnit.SECONDS)).isTrue();

        Thread waiter = new Thread(() -> {
            try {
                locks.withFloorLock(3, () -> "unreachable");
            } catch (Throwable t) {
                observed.set(t);
            } finally {
                done.countDown();
            }
        });
        waiter.start();
        assertThat(done.await(5, TimeUnit.SECONDS)).isTrue();
        release.countDown();
        holder.join(5000);

        assertThat(observed.get()).isInstanceOf(ApiException.class);
        assertThat(((ApiException) observed.get()).getErrorCode()).isEqualTo(ErrorCode.GATE_BUSY);
    }

    @Test
    @DisplayName("the lock is released even when the work throws")
    void lockReleasedOnException() {
        LotLockManager locks = manager(1000);

        assertThatThrownBy(() -> locks.withFloorLock(7, () -> {
            throw new IllegalStateException("boom");
        })).isInstanceOf(IllegalStateException.class);

        // If the lock leaked this would time out with GATE_BUSY.
        assertThat(locks.withFloorLock(7, () -> "ok")).isEqualTo("ok");
    }

    @Test
    @DisplayName("locks are re-entrant for nested use on the same floor")
    void locksAreReentrant() {
        LotLockManager locks = manager(1000);

        String result = locks.withFloorLock(1, () -> locks.withFloorLock(1, () -> "nested"));

        assertThat(result).isEqualTo("nested");
    }

    @Test
    @DisplayName("distinct tickets are locked independently")
    void ticketLocksAreIndependent() {
        LotLockManager locks = manager(1000);

        assertThat(locks.withTicketLock("PKT-1", () -> "a")).isEqualTo("a");
        assertThat(locks.withTicketLock("PKT-2", () -> "b")).isEqualTo("b");
        assertThat(locks.withTicketLock("PKT-1", () -> "a2")).isEqualTo("a2");
    }

    // ---------------------------------------------------------------- helpers

    private static void runConcurrently(int threads, Runnable work) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    work.run();
                } catch (Throwable t) {
                    t.printStackTrace();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertThat(done.await(30, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
