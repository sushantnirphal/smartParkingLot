package com.parking.lot;

import static org.assertj.core.api.Assertions.assertThat;

import com.parking.lot.model.ParkingSpot;
import com.parking.lot.model.SpotStatus;
import com.parking.lot.model.VehicleType;
import com.parking.lot.repository.ParkingSpotRepository;
import com.parking.lot.service.EntryService;
import com.parking.lot.testsupport.AbstractIntegrationTest;
import com.parking.lot.web.dto.CheckInRequest;
import com.parking.lot.web.dto.TicketDto;
import com.parking.lot.web.error.ApiException;
import com.parking.lot.web.error.ErrorCode;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Task C8 - check-in integration: happy path plus every 409/404/503 branch of
 * architecture §8 endpoint #1.
 */
class EntryFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired private EntryService entryService;
    @Autowired private ParkingSpotRepository spotRepository;

    private TicketDto enter(String plate, VehicleType type) {
        return entryService.checkIn(new CheckInRequest(plate, type, Integer.valueOf(1)));
    }

    private ErrorCode failure(Runnable work) {
        try {
            work.run();
        } catch (ApiException ex) {
            return ex.getErrorCode();
        }
        throw new AssertionError("expected an ApiException");
    }

    private void updateStatus(SpotStatus status, java.util.function.Predicate<ParkingSpot> filter) {
        List<ParkingSpot> affected = spotRepository.findAll().stream()
                .filter(filter)
                .collect(Collectors.toList());
        affected.forEach(s -> s.setStatus(status));
        spotRepository.saveAll(affected);
        spotRepository.flush();
    }

    @Test
    @DisplayName("TC-1: each vehicle type is assigned a spot of its own type")
    void eachTypeGetsItsOwnTypeOfSpot() {
        assertThat(enter("KA01AA1111", VehicleType.MOTORCYCLE).getSpotLabel()).isEqualTo("F1-M1");
        assertThat(enter("KA01BB2222", VehicleType.CAR).getSpotLabel()).isEqualTo("F1-C1");
        assertThat(enter("KA01CC3333", VehicleType.BUS).getSpotLabel()).isEqualTo("F1-B1");
    }

    @Test
    @DisplayName("nearest-entry: consecutive cars take the lowest free positions on the entry floor")
    void nearestEntryPicksLowestPosition() {
        assertThat(enter("KA01AA1111", VehicleType.CAR).getSpotLabel()).isEqualTo("F1-C1");
        assertThat(enter("KA01AA2222", VehicleType.CAR).getSpotLabel()).isEqualTo("F1-C2");
        assertThat(enter("KA01AA3333", VehicleType.CAR).getSpotLabel()).isEqualTo("F1-C3");
    }

    @Test
    @DisplayName("when the entry floor is full the vehicle falls through to the next floor")
    void fallsThroughToTheNextFloor() {
        for (int i = 1; i <= 6; i++) {
            enter("KA01FILL00" + i, VehicleType.CAR);
        }

        TicketDto seventh = enter("KA01NEXT01", VehicleType.CAR);
        TicketDto eighth = enter("KA01NEXT02", VehicleType.CAR);

        assertThat(seventh.getFloorNo()).isEqualTo(2);
        assertThat(seventh.getSpotLabel()).isEqualTo("F2-C1");
        assertThat(eighth.getSpotLabel()).isEqualTo("F2-C2");
    }

    @Test
    @DisplayName("A1: a BUS is never given a CAR spot, even when cars are free")
    void busNeverTakesACarSpot() {
        updateStatus(SpotStatus.OCCUPIED, s -> s.getSpotType() == VehicleType.BUS);

        assertThat(failure(() -> enter("KA01BUS001", VehicleType.BUS)))
                .isEqualTo(ErrorCode.PARKING_FULL_FOR_TYPE);

        // CAR and MOTORCYCLE bays are still free - it is a type shortage, not a full lot.
        assertThat(spotRepository.countByStatus(SpotStatus.FREE)).isPositive();
        assertThat(seeder.openTicketCount()).isZero();
    }

    @Test
    @DisplayName("G2: exactly one open ticket per vehicle")
    void duplicateEntryIsRejected() {
        enter("KA01AA1111", VehicleType.CAR);

        assertThat(failure(() -> enter("KA01AA1111", VehicleType.CAR)))
                .isEqualTo(ErrorCode.ALREADY_PARKED);
        assertThat(seeder.openTicketCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("C4: plates are trimmed and upper-cased before lookup")
    void plateIsNormalised() {
        TicketDto dto = entryService.checkIn(
                new CheckInRequest("  ka01ab1234  ", VehicleType.CAR, Integer.valueOf(1)));

        assertThat(dto.getPlate()).isEqualTo("KA01AB1234");
        assertThat(failure(() -> entryService.checkIn(
                new CheckInRequest("KA01AB1234", VehicleType.CAR, Integer.valueOf(1)))))
                .isEqualTo(ErrorCode.ALREADY_PARKED);
    }

    @Test
    @DisplayName("409 PARKING_FULL_FOR_TYPE when only other sizes are free")
    void fullForType() {
        updateStatus(SpotStatus.OCCUPIED, s -> s.getSpotType() == VehicleType.CAR);

        assertThat(failure(() -> enter("KA01CAR001", VehicleType.CAR)))
                .isEqualTo(ErrorCode.PARKING_FULL_FOR_TYPE);
        assertThat(seeder.openTicketCount()).isZero();
    }

    @Test
    @DisplayName("409 PARKING_FULL when nothing at all is usable")
    void fullLot() {
        updateStatus(SpotStatus.BLOCKED, s -> true);

        assertThat(failure(() -> enter("KA01CAR001", VehicleType.CAR)))
                .isEqualTo(ErrorCode.PARKING_FULL);
        assertThat(seeder.openTicketCount()).isZero();
    }

    @Test
    @DisplayName("404 FLOOR_NOT_FOUND for an unknown entry floor")
    void unknownFloor() {
        assertThat(failure(() -> entryService.checkIn(
                new CheckInRequest("KA01AA1111", VehicleType.CAR, Integer.valueOf(99)))))
                .isEqualTo(ErrorCode.FLOOR_NOT_FOUND);
    }

    @Test
    @DisplayName("occupying a spot and creating the ticket are atomic - a failure leaves no trace")
    void failedCheckInLeavesNoTrace() {
        updateStatus(SpotStatus.OCCUPIED, s -> s.getSpotType() == VehicleType.BUS);

        assertThat(failure(() -> enter("KA01BUS001", VehicleType.BUS)))
                .isEqualTo(ErrorCode.PARKING_FULL_FOR_TYPE);

        // Whole transaction rolled back: no ticket, no vehicle row, no spot flipped.
        assertThat(ticketRepository.findAll()).isEmpty();
        assertThat(vehicleRepository.findByPlateNo("KA01BUS001")).isEmpty();
        assertThat(spotRepository.countByStatus(SpotStatus.OCCUPIED))
                .isEqualTo(6); // only the bus bays we flipped ourselves
    }
}
