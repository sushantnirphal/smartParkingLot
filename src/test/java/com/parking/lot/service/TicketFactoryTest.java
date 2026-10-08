package com.parking.lot.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.parking.lot.model.FeeRate;
import com.parking.lot.model.ParkingFloor;
import com.parking.lot.model.ParkingSpot;
import com.parking.lot.model.ParkingTicket;
import com.parking.lot.model.PricingStrategyType;
import com.parking.lot.model.Vehicle;
import com.parking.lot.model.VehicleType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Task B7 - ticket factory: format, uniqueness, and the rate snapshot (architecture P3 / N4). */
class TicketFactoryTest {

    /** PKT-yyyyMMdd- + 10 uppercase hex chars = 23 chars, inside the VARCHAR(24) column. */
    private static final Pattern TICKET_NO = Pattern.compile("PKT-\\d{8}-[0-9A-F]{10}");

    private final TicketFactory factory = new TicketFactory();

    private static FeeRate rate() {
        return new FeeRate(VehicleType.CAR, PricingStrategyType.SLAB, 30, 120,
                java.math.BigDecimal.valueOf(40), 60, java.math.BigDecimal.valueOf(20),
                java.math.BigDecimal.valueOf(200), LocalDate.of(2026, 1, 1));
    }

    @Test
    void ticketNumberMatchesTheDocumentedFormat() {
        String ticketNo = factory.nextTicketNo(LocalDateTime.of(2026, 10, 7, 9, 15));

        assertThat(ticketNo).matches(TICKET_NO);
        assertThat(ticketNo).startsWith("PKT-20261007-");
        assertThat(ticketNo.length()).isLessThanOrEqualTo(24);
    }

    @Test
    @DisplayName("10k ticket numbers are unique - concurrent gates never collide")
    void ticketNumbersAreUnique() {
        Set<String> seen = new HashSet<String>();
        LocalDateTime now = LocalDateTime.of(2026, 10, 7, 9, 15);

        for (int i = 0; i < 10_000; i++) {
            seen.add(factory.nextTicketNo(now));
        }

        assertThat(seen).hasSize(10_000);
    }

    @Test
    @DisplayName("the opened ticket carries the rate snapshot taken at check-in (N4)")
    void openedTicketSnapshotsTheRate() {
        Vehicle vehicle = new Vehicle("KA01AB1234", VehicleType.CAR);
        ParkingFloor floor = new ParkingFloor(1);
        ParkingSpot spot = new ParkingSpot(floor, "F1-C1", VehicleType.CAR, 5);
        FeeRate rate = rate();
        LocalDateTime entry = LocalDateTime.of(2026, 10, 7, 9, 15);

        ParkingTicket ticket = factory.open(vehicle, spot, rate, entry);

        assertThat(ticket.getFeeRate()).isSameAs(rate);
        assertThat(ticket.getEntryTime()).isEqualTo(entry);
        assertThat(ticket.getStatus()).isEqualTo(com.parking.lot.model.TicketStatus.OPEN);
        assertThat(ticket.getTicketNo()).matches(TICKET_NO);
        assertThat(ticket.getExitTime()).isNull();
        assertThat(ticket.getAmountCharged()).isNull();
    }

    @Test
    @DisplayName("a ticket can never be opened without a rate - that is how R6 is enforced")
    void openingWithoutARateIsRejected() {
        Vehicle vehicle = new Vehicle("KA01AB1234", VehicleType.CAR);
        ParkingSpot spot = new ParkingSpot(new ParkingFloor(1), "F1-C1", VehicleType.CAR, 5);

        org.assertj.core.api.Assertions
                .assertThatThrownBy(() -> factory.open(vehicle, spot, null, LocalDateTime.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rate snapshot");
    }
}
