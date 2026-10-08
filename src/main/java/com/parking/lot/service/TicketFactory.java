package com.parking.lot.service;

import com.parking.lot.model.FeeRate;
import com.parking.lot.model.ParkingSpot;
import com.parking.lot.model.ParkingTicket;
import com.parking.lot.model.Vehicle;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Factory (architecture P3): the only place that mints ticket numbers, so no service
 * hand-rolls the format and the rate snapshot is always taken here (architecture N4).
 *
 * <p>Format {@code PKT-yyyyMMdd-XXXXXXXXXX} = 23 chars, inside the {@code VARCHAR(24)} column.
 * The 10 hex characters come from a UUID so concurrent gates never collide; the unique
 * constraint on {@code ticket_no} is the final backstop.
 */
@Component
public class TicketFactory {

    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("yyyyMMdd");

    public ParkingTicket open(Vehicle vehicle, ParkingSpot spot, FeeRate rateSnapshot, LocalDateTime entryTime) {
        if (rateSnapshot == null) {
            throw new IllegalArgumentException("A rate snapshot must be attached at check-in (N4 / R6)");
        }
        return new ParkingTicket(nextTicketNo(entryTime), vehicle, spot, rateSnapshot, entryTime);
    }

    public String nextTicketNo(LocalDateTime at) {
        String day = DAY.format(at);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10).toUpperCase();
        return "PKT-" + day + "-" + suffix;
    }
}
