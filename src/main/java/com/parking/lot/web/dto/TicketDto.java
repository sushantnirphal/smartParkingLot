package com.parking.lot.web.dto;

import com.parking.lot.model.ParkingTicket;
import com.parking.lot.model.TicketStatus;
import com.parking.lot.model.VehicleType;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/** Ticket projection - entities never cross the HTTP boundary (architecture P7 / D3). */
public class TicketDto {

    private final String ticketNo;
    private final String plate;
    private final VehicleType vehicleType;
    private final Integer floorNo;
    private final String spotLabel;
    private final Instant entryTime;
    private final Instant exitTime;
    private final TicketStatus status;
    private final Integer durationMinutes;

    public TicketDto(String ticketNo, String plate, VehicleType vehicleType, Integer floorNo,
                     String spotLabel, Instant entryTime, Instant exitTime,
                     TicketStatus status, Integer durationMinutes) {
        this.ticketNo = ticketNo;
        this.plate = plate;
        this.vehicleType = vehicleType;
        this.floorNo = floorNo;
        this.spotLabel = spotLabel;
        this.entryTime = entryTime;
        this.exitTime = exitTime;
        this.status = status;
        this.durationMinutes = durationMinutes;
    }

    public static TicketDto from(ParkingTicket t) {
        return new TicketDto(
                t.getTicketNo(),
                t.getVehicle().getPlateNo(),
                t.getVehicle().getVehicleType(),
                t.getSpot() == null || t.getSpot().getFloor() == null ? null : t.getSpot().getFloor().getFloorNo(),
                t.getSpot() == null ? null : t.getSpot().getLabel(),
                toInstant(t.getEntryTime()),
                toInstant(t.getExitTime()),
                t.getStatus(),
                t.getDurationMinutes());
    }

    static Instant toInstant(LocalDateTime time) {
        return time == null ? null : time.atZone(ZoneOffset.UTC).toInstant();
    }

    public String getTicketNo() { return ticketNo; }
    public String getPlate() { return plate; }
    public VehicleType getVehicleType() { return vehicleType; }
    public Integer getFloorNo() { return floorNo; }
    public String getSpotLabel() { return spotLabel; }
    public Instant getEntryTime() { return entryTime; }
    public Instant getExitTime() { return exitTime; }
    public TicketStatus getStatus() { return status; }
    public Integer getDurationMinutes() { return durationMinutes; }
}
