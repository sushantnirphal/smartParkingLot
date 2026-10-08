package com.parking.lot.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.parking.lot.model.FeeRate;
import com.parking.lot.model.ParkingFloor;
import com.parking.lot.model.ParkingSpot;
import com.parking.lot.model.PricingStrategyType;
import com.parking.lot.model.SpotStatus;
import com.parking.lot.model.Vehicle;
import com.parking.lot.model.VehicleType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.PageRequest;

/**
 * Task B6 - repository / custom SQL suite: the indexed allocation query (architecture §5),
 * the lock queries (§7) and the derived availability aggregate (N5).
 *
 * <p>{@code Replace.NONE} keeps the application's H2-in-MySQL-mode datasource so the Flyway
 * migrations - i.e. the real schema - are what gets exercised.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepositoryQueriesTest {

    @Autowired private VehicleRepository vehicleRepository;
    @Autowired private ParkingFloorRepository floorRepository;
    @Autowired private ParkingSpotRepository spotRepository;
    @Autowired private ParkingTicketRepository ticketRepository;
    @Autowired private FeeRateRepository feeRateRepository;

    @BeforeEach
    void seed() {
        // Inside the test transaction - rolled back after every method.
        // Hibernate runs queued INSERTs before DELETEs in one flush, so each delete must be
        // flushed before the next entity is created or uk_floor_no / uk_spot_label trip.
        ticketRepository.deleteAll();
        ticketRepository.flush();
        vehicleRepository.deleteAll();
        vehicleRepository.flush();
        spotRepository.deleteAll();
        spotRepository.flush();
        floorRepository.deleteAll();
        floorRepository.flush();

        ParkingFloor floor = floorRepository.save(new ParkingFloor(1));
        spotRepository.save(new ParkingSpot(floor, "F1-M1", VehicleType.MOTORCYCLE, 1));
        spotRepository.save(new ParkingSpot(floor, "F1-C3", VehicleType.CAR, 3));
        spotRepository.save(new ParkingSpot(floor, "F1-C1", VehicleType.CAR, 2));
        spotRepository.save(new ParkingSpot(floor, "F1-C2", VehicleType.CAR, 9));
        spotRepository.save(new ParkingSpot(floor, "F1-B1", VehicleType.BUS, 4));
        spotRepository.flush();
    }

    @Test
    @DisplayName("the Stage-1 candidate query returns only FREE spots of the requested type, ordered by position")
    void candidateQueryIsTypedAndOrdered() {
        List<ParkingSpot> candidates =
                spotRepository.findFreeByFloorAndType(1, VehicleType.CAR, SpotStatus.FREE);

        assertThat(candidates).extracting(ParkingSpot::getLabel)
                .containsExactly("F1-C1", "F1-C3", "F1-C2");   // positions 2, 3, 9
        assertThat(candidates).allMatch(s -> s.getSpotType() == VehicleType.CAR);
        assertThat(candidates).allMatch(s -> s.getStatus() == SpotStatus.FREE);
    }

    @Test
    @DisplayName("occupied and blocked spots drop out of the candidate query")
    void candidateQueryExcludesUnavailableSpots() {
        ParkingSpot taken = spotRepository.findAll().stream()
                .filter(s -> s.getLabel().equals("F1-C1")).findFirst().get();
        taken.setStatus(SpotStatus.OCCUPIED);
        spotRepository.save(taken);

        List<ParkingSpot> candidates =
                spotRepository.findFreeByFloorAndType(1, VehicleType.CAR, SpotStatus.FREE);

        assertThat(candidates).extracting(ParkingSpot::getLabel).containsExactly("F1-C3", "F1-C2");
        assertThat(spotRepository.countByStatus(SpotStatus.FREE)).isEqualTo(4);
    }

    @Test
    @DisplayName("availability aggregate is derived from rows (A7 / N5) - no counter column")
    void availabilityAggregateIsDerived() {
        List<Object[]> rows = spotRepository.countByFloorTypeAndStatus();

        assertThat(rows).isNotEmpty();
        Object[] carFree = rows.stream()
                .filter(r -> Integer.valueOf(1).equals(r[0])
                        && VehicleType.CAR == r[1]
                        && SpotStatus.FREE == r[2])
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing CAR/FREE row"));

        assertThat(((Long) carFree[3]).longValue()).isEqualTo(3L);
    }

    @Test
    @DisplayName("the pessimistic read lock resolves (architecture §7 layer 3)")
    void pessimisticLockResolves() {
        ParkingSpot spot = spotRepository.findFreeByFloorAndType(1, VehicleType.CAR, SpotStatus.FREE).get(0);

        assertThat(spotRepository.findForUpdateById(spot.getId())).isPresent();
    }

    @Test
    @DisplayName("the open-ticket duplicate check answers threat R3")
    void openTicketLookup() {
        Vehicle vehicle = vehicleRepository.saveAndFlush(new Vehicle("KA01AB1234", VehicleType.CAR));
        ParkingFloor floor = floorRepository.findAll().get(0);
        ParkingSpot spot = spotRepository.findAll().stream().findFirst().get();

        assertThat(ticketRepository.existsByVehicleIdAndStatus(vehicle.getId(),
                com.parking.lot.model.TicketStatus.OPEN)).isFalse();

        com.parking.lot.model.ParkingTicket ticket = new com.parking.lot.model.ParkingTicket(
                "PKT-20261007-TESTTEST01", vehicle, spot, null, java.time.LocalDateTime.now());
        ticketRepository.saveAndFlush(ticket);

        assertThat(ticketRepository.existsByVehicleIdAndStatus(vehicle.getId(),
                com.parking.lot.model.TicketStatus.OPEN)).isTrue();
        assertThat(ticketRepository.findByTicketNo("PKT-20261007-TESTTEST01")).isPresent();
        assertThat(floor).isNotNull();
    }

    @Test
    @DisplayName("rate selection ignores cards that are not yet valid and prefers the newest (N3 / R6)")
    void rateSelectionIsDateAware() {
        LocalDate today = LocalDate.now();

        FeeRate future = new FeeRate(VehicleType.CAR, PricingStrategyType.SLAB, 0, 60,
                new BigDecimal("999.00"), null, null, null, today.plusDays(30));
        feeRateRepository.save(future);

        FeeRate selected = feeRateRepository
                .findTop1ByVehicleTypeAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDesc(
                        VehicleType.CAR, today)
                .orElseThrow(() -> new AssertionError("rate missing"));

        assertThat(selected.getBaseAmount()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(selected.getStrategy()).isEqualTo(PricingStrategyType.SLAB);
        assertThat(selected.getFreeMinutes()).isEqualTo(30);
    }

    @Test
    @DisplayName("an inactive card is never selected")
    void inactiveRatesAreIgnored() {
        LocalDate today = LocalDate.now();
        feeRateRepository.findAll().forEach(r -> r.setActive(Boolean.FALSE));

        assertThat(feeRateRepository
                .findTop1ByVehicleTypeAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDesc(
                        VehicleType.CAR, today)).isEmpty();
    }

    @Test
    @DisplayName("paging on the ticket listing respects the requested window (rule C8)")
    void ticketPaging() {
        Vehicle vehicle = vehicleRepository.saveAndFlush(new Vehicle("KA01AB1234", VehicleType.CAR));
        ParkingSpot spot = spotRepository.findAll().stream().findFirst().get();
        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        for (int i = 0; i < 5; i++) {
            ticketRepository.save(new com.parking.lot.model.ParkingTicket(
                    "PKT-20261007-PAGE00000" + i, vehicle, spot, null, now));
        }
        ticketRepository.flush();

        List<com.parking.lot.model.ParkingTicket> page =
                ticketRepository.findByVehicle_PlateNoOrderByEntryTimeDesc("KA01AB1234", PageRequest.of(0, 2));

        assertThat(page).hasSize(2);
        assertThat(ticketRepository.findAll().stream()
                .map(t -> t.getTicketNo()).collect(Collectors.toList())).hasSize(5);
    }
}
