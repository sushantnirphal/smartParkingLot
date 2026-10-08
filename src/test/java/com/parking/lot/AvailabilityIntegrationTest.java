package com.parking.lot;

import static org.assertj.core.api.Assertions.assertThat;

import com.parking.lot.model.SpotStatus;
import com.parking.lot.model.VehicleType;
import com.parking.lot.repository.ParkingFloorRepository;
import com.parking.lot.repository.ParkingSpotRepository;
import com.parking.lot.service.AvailabilityService;
import com.parking.lot.service.EntryService;
import com.parking.lot.service.ExitService;
import com.parking.lot.testsupport.AbstractIntegrationTest;
import com.parking.lot.web.dto.AvailabilityDto;
import com.parking.lot.web.dto.CheckInRequest;
import com.parking.lot.web.dto.TicketDto;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Task E3 / acceptance TC-4 - real-time availability is derived from spot rows (A7 / N5)
 * and changes immediately on entry and exit.
 */
class AvailabilityIntegrationTest extends AbstractIntegrationTest {

    @Autowired private EntryService entryService;
    @Autowired private ExitService exitService;
    @Autowired private AvailabilityService availabilityService;
    @Autowired private ParkingSpotRepository spotRepository;
    @Autowired private ParkingFloorRepository floorRepository;

    private int freeOnFloor(int floorNo, VehicleType type) {
        return availabilityService.current().getFloors().stream()
                .filter(f -> f.getFloorNo() == floorNo)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no floor " + floorNo))
                .getFree().get(type).intValue();
    }

    private int totalOnFloor(int floorNo, VehicleType type) {
        return availabilityService.current().getFloors().stream()
                .filter(f -> f.getFloorNo() == floorNo)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no floor " + floorNo))
                .getTotal().get(type).intValue();
    }

    @Test
    @DisplayName("TC-4: the seeded lot reports its real capacity per floor and type")
    void reportsSeededCapacity() {
        AvailabilityDto dto = availabilityService.current();

        assertThat(dto.getFloors()).hasSize(3);
        assertThat(totalOnFloor(1, VehicleType.MOTORCYCLE)).isEqualTo(4);
        assertThat(totalOnFloor(1, VehicleType.CAR)).isEqualTo(6);
        assertThat(totalOnFloor(1, VehicleType.BUS)).isEqualTo(2);
        assertThat(freeOnFloor(1, VehicleType.CAR)).isEqualTo(6);
        assertThat(dto.getAsOf()).isNotNull();
    }

    @Test
    @DisplayName("TC-4: an entry drops the free count for that floor and type immediately")
    void entryDropsFreeCount() {
        long before = availabilityService.current().getLotVersion();

        entryService.checkIn(new CheckInRequest("KA01CAR001", VehicleType.CAR, Integer.valueOf(1)));

        assertThat(freeOnFloor(1, VehicleType.CAR)).isEqualTo(5);
        assertThat(freeOnFloor(1, VehicleType.BUS)).isEqualTo(2);  // untouched
        assertThat(freeOnFloor(2, VehicleType.CAR)).isEqualTo(6);  // untouched
        assertThat(availabilityService.current().getLotVersion()).isGreaterThan(before);
    }

    @Test
    @DisplayName("TC-4: an exit restores the free count")
    void exitRestoresFreeCount() {
        TicketDto ticket = entryService.checkIn(
                new CheckInRequest("KA01CAR001", VehicleType.CAR, Integer.valueOf(1)));
        assertThat(freeOnFloor(1, VehicleType.CAR)).isEqualTo(5);

        exitService.checkOut(ticket.getTicketNo(),
                java.time.LocalDateTime.ofInstant(
                        ticket.getEntryTime().plus(5, ChronoUnit.MINUTES), java.time.ZoneOffset.UTC),
                false);

        assertThat(freeOnFloor(1, VehicleType.CAR)).isEqualTo(6);
    }

    @Test
    @DisplayName("TC-4: counts always equal the spot rows - no drift after mixed traffic")
    void derivedCountsNeverDrift() {
        for (int i = 1; i <= 4; i++) {
            entryService.checkIn(new CheckInRequest("KA01CAR00" + i, VehicleType.CAR, Integer.valueOf(1)));
        }
        TicketDto first = entryService.checkIn(
                new CheckInRequest("KA01CAR005", VehicleType.CAR, Integer.valueOf(1)));
        exitService.checkOut(first.getTicketNo(),
                java.time.LocalDateTime.ofInstant(
                        first.getEntryTime().plus(3, ChronoUnit.MINUTES), java.time.ZoneOffset.UTC),
                false);

        long freeRows = spotRepository.findAll().stream()
                .filter(s -> s.getStatus() == SpotStatus.FREE).count();
        long reportedFree = availabilityService.current().getFloors().stream()
                .mapToLong(f -> f.getFree().values().stream().mapToLong(Integer::longValue).sum())
                .sum();

        assertThat(reportedFree).isEqualTo(freeRows);
        assertThat(seeder.openTicketCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("lotVersion only moves forward, so a stale read is always detectable")
    void lotVersionIsMonotonic() {
        long v0 = availabilityService.current().getLotVersion();

        TicketDto ticket = entryService.checkIn(
                new CheckInRequest("KA01CAR001", VehicleType.CAR, Integer.valueOf(1)));
        long v1 = availabilityService.current().getLotVersion();

        exitService.checkOut(ticket.getTicketNo(),
                java.time.LocalDateTime.ofInstant(
                        ticket.getEntryTime().plus(1, ChronoUnit.MINUTES), java.time.ZoneOffset.UTC),
                false);
        long v2 = availabilityService.current().getLotVersion();

        assertThat(v0).isLessThanOrEqualTo(v1);
        assertThat(v1).isLessThan(v2);
    }

    @Test
    @DisplayName("the raw spot listing endpoint reports floors, positions and statuses")
    void rawSpotListing() {
        entryService.checkIn(new CheckInRequest("KA01CAR001", VehicleType.CAR, Integer.valueOf(1)));

        java.util.List<com.parking.lot.web.dto.SpotDto> spots =
                availabilityService.spotsOnFloor(1, SpotStatus.FREE, VehicleType.CAR,
                        org.springframework.data.domain.PageRequest.of(0, 100));

        assertThat(spots).hasSize(5);
        assertThat(spots.get(0).getFloorNo()).isEqualTo(1);
        assertThat(spots.get(0).getPosition()).isEqualTo(6); // F1-C1 is taken
    }

    @Test
    @DisplayName("404 FLOOR_NOT_FOUND for the raw spot listing")
    void unknownFloorOnSpotListing() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> availabilityService.spotsOnFloor(
                        42, SpotStatus.FREE, null, org.springframework.data.domain.PageRequest.of(0, 10)))
                .isInstanceOf(com.parking.lot.web.error.ApiException.class)
                .extracting(e -> ((com.parking.lot.web.error.ApiException) e).getErrorCode())
                .isEqualTo(com.parking.lot.web.error.ErrorCode.FLOOR_NOT_FOUND);
    }

    @Test
    @DisplayName("every seeded floor exists exactly once")
    void floorsAreSeeded() {
        assertThat(floorRepository.findAll()).hasSize(3);
        assertThat(spotRepository.findAll()).hasSize(36);
    }

    @Test
    @DisplayName("the availability snapshot carries an asOf timestamp (contract rule C5)")
    void snapshotHasTimestamp() {
        AvailabilityDto dto = availabilityService.current();
        Instant asOf = dto.getAsOf();

        assertThat(asOf).isNotNull();
        assertThat(asOf).isBeforeOrEqualTo(Instant.now().plusSeconds(5));
    }
}
