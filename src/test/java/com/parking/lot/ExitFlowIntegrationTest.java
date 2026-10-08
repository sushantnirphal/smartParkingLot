package com.parking.lot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.parking.lot.model.SpotStatus;
import com.parking.lot.model.VehicleType;
import com.parking.lot.repository.ParkingSpotRepository;
import com.parking.lot.service.EntryService;
import com.parking.lot.service.ExitService;
import com.parking.lot.testsupport.AbstractIntegrationTest;
import com.parking.lot.web.dto.CheckInRequest;
import com.parking.lot.web.dto.ReceiptDto;
import com.parking.lot.web.dto.TicketDto;
import com.parking.lot.web.error.ApiException;
import com.parking.lot.web.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Task D9 - check-out integration: fee receipt, idempotent replay (rule C3) and the
 * validation branches of architecture §8 endpoint #2.
 */
class ExitFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired private EntryService entryService;
    @Autowired private ExitService exitService;
    @Autowired private ParkingSpotRepository spotRepository;

    private TicketDto park(String plate, VehicleType type) {
        return entryService.checkIn(new CheckInRequest(plate, type, Integer.valueOf(1)));
    }

    private ReceiptDto exit(String ticketNo, Instant exitTime, boolean replay) {
        return exitService.checkOut(ticketNo, exitTime == null ? null
                : java.time.LocalDateTime.ofInstant(exitTime, java.time.ZoneOffset.UTC), replay);
    }

    @Test
    @DisplayName("TC-3: entry -> 45 min -> exit on a CAR bills exactly 40.00 (architecture §6)")
    void fullStayIsBilledFromTheWorkedExamples() {
        TicketDto ticket = park("KA01CAR001", VehicleType.CAR);
        Instant exitTime = ticket.getEntryTime().plus(45, ChronoUnit.MINUTES);

        ReceiptDto receipt = exit(ticket.getTicketNo(), exitTime, false);

        assertThat(receipt.getAmount()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(receipt.getDurationMinutes()).isEqualTo(45);
        assertThat(receipt.getRateCard()).isEqualTo("CAR-SLAB");
        assertThat(receipt.getCurrency()).isEqualTo("INR");
        assertThat(receipt.isReplay()).isFalse();
        assertThat(receipt.getExitTime()).isEqualTo(exitTime);
        assertThat(receipt.getPlate()).isEqualTo("KA01CAR001");
    }

    @Test
    @DisplayName("G2: timestamps are recorded on both sides of the stay")
    void timestampsAreRecorded() {
        TicketDto ticket = park("KA01CAR001", VehicleType.CAR);
        Instant exitTime = ticket.getEntryTime().plus(10, ChronoUnit.MINUTES);

        ReceiptDto receipt = exit(ticket.getTicketNo(), exitTime, false);

        assertThat(receipt.getEntryTime()).isEqualTo(ticket.getEntryTime());
        assertThat(receipt.getExitTime()).isEqualTo(exitTime);
        assertThat(receipt.getDurationMinutes()).isEqualTo(10);
    }

    @Test
    @DisplayName("G4: exiting frees the spot again")
    void exitFreesTheSpot() {
        TicketDto ticket = park("KA01CAR001", VehicleType.CAR);
        String label = ticket.getSpotLabel();
        assertThat(spot(label).getStatus()).isEqualTo(SpotStatus.OCCUPIED);

        exit(ticket.getTicketNo(), ticket.getEntryTime().plus(5, ChronoUnit.MINUTES), false);

        assertThat(spot(label).getStatus()).isEqualTo(SpotStatus.FREE);
        assertThat(seeder.openTicketCount()).isZero();
    }

    @Test
    @DisplayName("404 TICKET_NOT_FOUND for an unknown ticket")
    void unknownTicket() {
        assertThatThrownBy(() -> exit("PKT-20261007-DEADBEEF00", Instant.now(), false))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.TICKET_NOT_FOUND);
    }

    @Test
    @DisplayName("threat R4: a second exit is rejected instead of charging twice")
    void secondExitIsRejected() {
        TicketDto ticket = park("KA01CAR001", VehicleType.CAR);
        Instant exitTime = ticket.getEntryTime().plus(45, ChronoUnit.MINUTES);

        ReceiptDto first = exit(ticket.getTicketNo(), exitTime, false);

        assertThatThrownBy(() -> exit(ticket.getTicketNo(), exitTime, false))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.TICKET_ALREADY_CLOSED);

        // exactly one charge exists in the database
        BigDecimal stored = ticketRepository.findByTicketNo(ticket.getTicketNo()).get().getAmountCharged();
        assertThat(stored).isEqualByComparingTo(first.getAmount());
    }

    @Test
    @DisplayName("rule C3: ?replay=true returns the original receipt, never a second charge")
    void idempotentReplayReturnsTheOriginalReceipt() {
        TicketDto ticket = park("KA01CAR001", VehicleType.CAR);
        Instant exitTime = ticket.getEntryTime().plus(45, ChronoUnit.MINUTES);

        ReceiptDto first = exit(ticket.getTicketNo(), exitTime, false);
        ReceiptDto replayed = exit(ticket.getTicketNo(), exitTime, true);

        assertThat(replayed.isReplay()).isTrue();
        assertThat(replayed.getAmount()).isEqualByComparingTo(first.getAmount());
        assertThat(replayed.getExitTime()).isEqualTo(first.getExitTime());
    }

    @Test
    @DisplayName("rule R5: an exit before entry is a 400, never a negative fee")
    void exitBeforeEntryIsRejected() {
        TicketDto ticket = park("KA01CAR001", VehicleType.CAR);
        Instant beforeEntry = ticket.getEntryTime().minus(5, ChronoUnit.MINUTES);

        assertThatThrownBy(() -> exit(ticket.getTicketNo(), beforeEntry, false))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getErrorCode())
                .isEqualTo(ErrorCode.INVALID_EXIT_TIME);

        assertThat(seeder.openTicketCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("omitted exitTime falls back to the server clock (assumption A4)")
    void omittedExitTimeUsesTheServerClock() {
        TicketDto ticket = park("KA01CAR001", VehicleType.CAR);

        ReceiptDto receipt = exit(ticket.getTicketNo(), null, false);

        assertThat(receipt.getExitTime()).isNotNull();
        assertThat(receipt.getExitTime()).isAfterOrEqualTo(ticket.getEntryTime());
        assertThat(receipt.getAmount()).isEqualByComparingTo(new BigDecimal("0.00"));
    }

    @Test
    @DisplayName("TC-2: a vehicle may park again after it has checked out")
    void vehicleCanParkAgainAfterExit() {
        TicketDto first = park("KA01CAR001", VehicleType.CAR);
        exit(first.getTicketNo(), first.getEntryTime().plus(5, ChronoUnit.MINUTES), false);

        TicketDto second = park("KA01CAR001", VehicleType.CAR);

        assertThat(second.getTicketNo()).isNotEqualTo(first.getTicketNo());
        assertThat(seeder.openTicketCount()).isEqualTo(1);
    }

    private com.parking.lot.model.ParkingSpot spot(String label) {
        return spotRepository.findAll().stream()
                .filter(s -> s.getLabel().equals(label))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no spot " + label));
    }
}
