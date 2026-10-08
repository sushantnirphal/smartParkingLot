package com.parking.lot;

import static org.assertj.core.api.Assertions.assertThat;

import com.parking.lot.model.SpotStatus;
import com.parking.lot.model.TicketStatus;
import com.parking.lot.model.VehicleType;
import com.parking.lot.service.EntryService;
import com.parking.lot.web.dto.CheckInRequest;
import com.parking.lot.web.error.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Task F2 - risk RK1: the concurrency guarantees are re-verified against a real MySQL 8,
 * because H2 does not reproduce InnoDB's locking and gap behaviour.
 *
 * <p>Automatically skipped when Docker is unavailable; run it in CI where Docker is present:
 * {@code mvn test -Dtest=F2MySqlConcurrencyIT}.
 */
@SpringBootTest(properties = "parking.lock-timeout-ms=180000")
@Testcontainers(disabledWithoutDocker = true)
class F2MySqlConcurrencyIT {

    private static final int GATES = 60;
    private static final int SPOTS = 10;

    @Container
    static MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("parking_lot")
            .withUsername("park")
            .withPassword("park");

    @DynamicPropertySource
    static void mysqlProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", MYSQL::getJdbcUrl);
        registry.add("spring.datasource.username", MYSQL::getUsername);
        registry.add("spring.datasource.password", MYSQL::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "com.mysql.cj.jdbc.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.MySQL8Dialect");
    }

    @Autowired private EntryService entryService;
    @Autowired private com.parking.lot.repository.ParkingFloorRepository floorRepository;
    @Autowired private com.parking.lot.repository.ParkingSpotRepository spotRepository;
    @Autowired private com.parking.lot.repository.ParkingTicketRepository ticketRepository;
    @Autowired private com.parking.lot.repository.VehicleRepository vehicleRepository;
    @Autowired private com.parking.lot.cache.AvailabilityCache availabilityCache;

    @Test
    @DisplayName("InnoDB: 60 gates / 10 spots still yields exactly 10 winners and 10 distinct spots")
    void noDoubleBookingOnRealMysql() throws Exception {
        // Flyway has already created the schema and seeded the rate card on the container.
        com.parking.lot.testsupport.TestLotSeeder seeder = new com.parking.lot.testsupport.TestLotSeeder(
                floorRepository, spotRepository, ticketRepository, vehicleRepository, availabilityCache);
        seeder.resetSingleFloor(1, 0, SPOTS, 0);

        ExecutorService pool = Executors.newFixedThreadPool(20);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<Future<String>>();
        try {
            for (int i = 0; i < GATES; i++) {
                final String plate = "MYSQL" + i;
                futures.add(pool.submit(() -> {
                    start.await();
                    try {
                        entryService.checkIn(new CheckInRequest(plate, VehicleType.CAR, Integer.valueOf(1)));
                        return "OK";
                    } catch (ApiException e) {
                        return e.getErrorCode().getCode();
                    } catch (RuntimeException e) {
                        return "ERROR:" + e.getClass().getSimpleName();
                    }
                }));
            }
            start.countDown();

            List<String> results = new ArrayList<String>();
            for (Future<String> future : futures) {
                results.add(future.get(120, TimeUnit.SECONDS));
            }

            long winners = results.stream().filter("OK"::equals).count();
            assertThat(winners).isEqualTo(SPOTS);
            assertThat(results.stream().filter(r -> r.startsWith("ERROR"))).isEmpty();
            assertThat(spotRepository.countByStatus(SpotStatus.OCCUPIED)).isEqualTo(SPOTS);
            assertThat(ticketRepository.findDistinctSpotIdsByStatus(TicketStatus.OPEN)).hasSize(SPOTS);
        } finally {
            pool.shutdownNow();
        }
    }
}
