package com.parking.lot;

import static org.assertj.core.api.Assertions.assertThat;

import com.parking.lot.model.SpotStatus;
import com.parking.lot.model.VehicleType;
import com.parking.lot.model.TicketStatus;
import com.parking.lot.service.AllocationService;
import com.parking.lot.service.EntryService;
import com.parking.lot.service.ExitService;
import com.parking.lot.testsupport.AbstractIntegrationTest;
import com.parking.lot.testsupport.TestLotSeeder;
import com.parking.lot.web.dto.CheckInRequest;
import com.parking.lot.web.dto.ReceiptDto;
import com.parking.lot.web.dto.TicketDto;
import com.parking.lot.web.error.ApiException;
import com.parking.lot.web.error.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Task F1 + F3 - the five concurrency scenarios of architecture §7.
 *
 * <p>Runs with a generous lock timeout so contention resolves as business outcomes
 * (PARKING_FULL / ALREADY_PARKED / TICKET_ALREADY_CLOSED) rather than GATE_BUSY noise;
 * the 2 s gate timeout itself is covered by {@code LotLockManagerTest}.
 */
@SpringBootTest(properties = "parking.lock-timeout-ms=120000")
class ParkingConcurrencyTest extends AbstractIntegrationTest {

    private static final int GATES = 100;
    private static final int SPOTS = 10;

    @Autowired private EntryService entryService;
    @Autowired private ExitService exitService;
    @Autowired private AllocationService allocationService;
    @Autowired private PlatformTransactionManager transactionManager;

    // ------------------------------------------------- scenario 1: no double booking

    @Test
    @DisplayName("§7-1: 100 gates, 10 spots -> exactly 10 winners and 10 distinct spots")
    void hundredGatesTenSpots() throws Exception {
        seeder.resetSingleFloor(1, 0, SPOTS, 0);

        List<String> results = runAll(GATES, i -> {
            try {
                entryService.checkIn(new CheckInRequest("CONC" + i, VehicleType.CAR, Integer.valueOf(1)));
                return "OK";
            } catch (ApiException e) {
                return e.getErrorCode().getCode();
            } catch (RuntimeException e) {
                return "ERROR:" + e.getClass().getSimpleName();
            }
        });

        long winners = results.stream().filter("OK"::equals).count();
        long losers = results.size() - winners;

        assertThat(winners).isEqualTo(SPOTS);
        assertThat(losers).isEqualTo(GATES - SPOTS);
        // The single-floor fixture has no other bays, so the lot is genuinely full once the
        // 10 CAR bays are gone - both codes are legitimate answers for a rejected gate.
        assertThat(results).allMatch(r -> "OK".equals(r)
                || "PARKING_FULL".equals(r)
                || "PARKING_FULL_FOR_TYPE".equals(r));
        assertThat(results).contains("PARKING_FULL");

        // 10 OCCUPIED rows, 10 open tickets, 10 distinct spots -> no double booking (G5)
        assertThat(spotRepository.countByStatus(SpotStatus.OCCUPIED)).isEqualTo(SPOTS);
        assertThat(seeder.openTicketCount()).isEqualTo(SPOTS);
        assertThat(ticketRepository.findDistinctSpotIdsByStatus(TicketStatus.OPEN))
                .hasSize(SPOTS);
    }

    // ------------------------------------------------- scenario 2: one open ticket

    @Test
    @DisplayName("§7-2: simultaneous check-in of the same plate yields exactly one ticket")
    void duplicateCheckInIsSerialised() throws Exception {
        List<String> results = runAll(8, i -> {
            try {
                entryService.checkIn(new CheckInRequest("RACE0001", VehicleType.CAR, Integer.valueOf(1)));
                return "OK";
            } catch (ApiException e) {
                return e.getErrorCode().getCode();
            } catch (RuntimeException e) {
                return "ERROR:" + e.getClass().getSimpleName();
            }
        });

        assertThat(results.stream().filter("OK"::equals)).hasSize(1);
        assertThat(results.stream().filter(r -> r.startsWith("ERROR"))).isEmpty();
        assertThat(results).contains(ErrorCode.ALREADY_PARKED.getCode());
        assertThat(seeder.openTicketCount()).isEqualTo(1);
    }

    // ------------------------------------------------- scenario 3: one receipt

    @Test
    @DisplayName("§7-3: concurrent exits of one ticket produce exactly one charge")
    void duplicateExitIsSerialised() throws Exception {
        TicketDto ticket = park("RACEEXIT1");
        LocalDateTime exitTime = LocalDateTime.ofInstant(
                ticket.getEntryTime().plus(45, java.time.temporal.ChronoUnit.MINUTES), java.time.ZoneOffset.UTC);

        List<String> results = runAll(8, i -> {
            try {
                ReceiptDto receipt = exitService.checkOut(ticket.getTicketNo(), exitTime, false);
                return "OK:" + receipt.getAmount().toPlainString();
            } catch (ApiException e) {
                return e.getErrorCode().getCode();
            } catch (RuntimeException e) {
                return "ERROR:" + e.getClass().getSimpleName();
            }
        });

        long winners = results.stream().filter(r -> r.startsWith("OK")).count();
        assertThat(winners).isEqualTo(1);
        assertThat(results.stream().filter(r -> r.startsWith("ERROR"))).isEmpty();
        assertThat(results).contains(ErrorCode.TICKET_ALREADY_CLOSED.getCode());

        BigDecimal stored = ticketRepository.findByTicketNo(ticket.getTicketNo()).get().getAmountCharged();
        assertThat(stored).isEqualByComparingTo(new BigDecimal("40.00"));
    }

    // ------------------------------------------------- scenario 4: availability stays honest

    @Test
    @DisplayName("§7-4: entries racing exits leave every occupied spot with exactly one ticket")
    void entryRacingExitKeepsAvailabilityCorrect() throws Exception {
        seeder.resetSingleFloor(1, 0, SPOTS, 0);
        for (int i = 0; i < 5; i++) {
            park("PRE" + i);
        }
        List<String> exitingTickets = new ArrayList<String>();
        for (com.parking.lot.model.ParkingTicket t : ticketRepository.findAll()) {
            exitingTickets.add(t.getTicketNo());
        }

        AtomicInteger cursor = new AtomicInteger();
        List<java.util.concurrent.Callable<String>> jobs = new ArrayList<java.util.concurrent.Callable<String>>();
        for (final String ticketNo : exitingTickets) {
            jobs.add(() -> {
                try {
                    exitService.checkOut(ticketNo, LocalDateTime.now(java.time.Clock.systemUTC()).plusMinutes(5), false);
                    return "EXIT_OK";
                } catch (RuntimeException e) {
                    return "EXIT_ERROR:" + e.getClass().getSimpleName();
                }
            });
        }
        for (int i = 0; i < 5; i++) {
            jobs.add(() -> {
                int n = cursor.incrementAndGet();
                try {
                    entryService.checkIn(new CheckInRequest("DURING" + n, VehicleType.CAR, Integer.valueOf(1)));
                    return "ENTER_OK";
                } catch (RuntimeException e) {
                    return "ENTER_ERROR:" + e.getClass().getSimpleName();
                }
            });
        }
        Collections.shuffle(jobs);

        List<String> results = invokeAll(jobs);

        assertThat(results.stream().filter(r -> r.endsWith("_ERROR"))).isEmpty();
        // 5 entered and 5 exited -> still 10 spots, 5 occupied, 5 free, 5 open tickets
        assertThat(spotRepository.findAll()).hasSize(SPOTS);
        assertThat(spotRepository.countByStatus(SpotStatus.OCCUPIED)).isEqualTo(5);
        assertThat(spotRepository.countByStatus(SpotStatus.FREE)).isEqualTo(5);
        assertThat(seeder.openTicketCount()).isEqualTo(5);
        // every occupied spot carries a ticket, and no ticket points at a free spot
        assertThat(ticketRepository.findDistinctSpotIdsByStatus(TicketStatus.OPEN)).hasSize(5);
    }

    // ------------------------------------------------- scenario 5: rollback discipline

    @Test
    @DisplayName("§7-5: a rollback after the spot was occupied returns it to FREE with no ticket")
    void rollbackReturnsSpotToFree() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        final com.parking.lot.model.ParkingSpot[] touched = new com.parking.lot.model.ParkingSpot[1];

        template.execute(status -> {
            touched[0] = allocationService.allocate(VehicleType.CAR, 1);
            assertThat(touched[0].getStatus()).isEqualTo(SpotStatus.OCCUPIED);
            status.setRollbackOnly();
            return null;
        });

        assertThat(touched[0]).isNotNull();
        assertThat(spotRepository.findById(touched[0].getId()).get().getStatus())
                .isEqualTo(SpotStatus.FREE);
        assertThat(ticketRepository.count()).isZero();
        assertThat(spotRepository.countByStatus(SpotStatus.FREE)).isEqualTo(36);
    }

    // ------------------------------------------------------------------- helpers

    private TicketDto park(String plate) {
        return entryService.checkIn(new CheckInRequest(plate, VehicleType.CAR, Integer.valueOf(1)));
    }

    /** Runs the jobs concurrently, all released by the same start latch. */
    private List<String> runAll(int count, java.util.function.IntFunction<String> job) throws Exception {
        List<java.util.concurrent.Callable<String>> jobs = new ArrayList<java.util.concurrent.Callable<String>>();
        for (int i = 0; i < count; i++) {
            final int index = i;
            jobs.add(() -> job.apply(index));
        }
        return invokeAll(jobs);
    }

    private List<String> invokeAll(List<java.util.concurrent.Callable<String>> jobs) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(25, jobs.size()));
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<Future<String>>();
        try {
            for (final java.util.concurrent.Callable<String> job : jobs) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return job.call();
                }));
            }
            start.countDown();

            List<String> results = new ArrayList<String>(futures.size());
            for (Future<String> future : futures) {
                results.add(future.get(60, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            pool.shutdownNow();
        }
    }

    /** Guards against a silent map/ordering regression in the seeder itself. */
    @Test
    @DisplayName("the concurrency fixture really has the expected capacity")
    void fixtureIsSane() {
        seeder.resetSingleFloor(1, 0, SPOTS, 0);
        Map<String, Integer> counts = new TreeMap<String, Integer>();
        spotRepository.findAll().forEach(s -> counts.merge(s.getSpotType().name(), 1, Integer::sum));
        assertThat(counts).containsEntry("CAR", SPOTS);
        assertThat(TestLotSeeder.class).isNotNull();
    }
}
