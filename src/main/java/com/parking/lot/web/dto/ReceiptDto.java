package com.parking.lot.web.dto;

import com.parking.lot.model.ParkingTicket;
import com.parking.lot.model.VehicleType;
import java.math.BigDecimal;
import java.time.Instant;

/** Exit response (architecture §8 #2). Money is always 2dp with an explicit currency (C6). */
public class ReceiptDto {

    private final String ticketNo;
    private final String plate;
    private final VehicleType vehicleType;
    private final String spotLabel;
    private final Integer floorNo;
    private final Instant entryTime;
    private final Instant exitTime;
    private final Integer durationMinutes;
    private final String rateCard;
    private final BigDecimal amount;
    private final String currency;
    private final boolean replay;

    public ReceiptDto(String ticketNo, String plate, VehicleType vehicleType, String spotLabel,
                      Integer floorNo, Instant entryTime, Instant exitTime, Integer durationMinutes,
                      String rateCard, BigDecimal amount, String currency, boolean replay) {
        this.ticketNo = ticketNo;
        this.plate = plate;
        this.vehicleType = vehicleType;
        this.spotLabel = spotLabel;
        this.floorNo = floorNo;
        this.entryTime = entryTime;
        this.exitTime = exitTime;
        this.durationMinutes = durationMinutes;
        this.rateCard = rateCard;
        this.amount = amount;
        this.currency = currency;
        this.replay = replay;
    }

    public static ReceiptDto from(ParkingTicket t, String currency, boolean replay) {
        return new ReceiptDto(
                t.getTicketNo(),
                t.getVehicle().getPlateNo(),
                t.getVehicle().getVehicleType(),
                t.getSpot() == null ? null : t.getSpot().getLabel(),
                t.getSpot() == null || t.getSpot().getFloor() == null ? null : t.getSpot().getFloor().getFloorNo(),
                TicketDto.toInstant(t.getEntryTime()),
                TicketDto.toInstant(t.getExitTime()),
                t.getDurationMinutes(),
                t.getFeeRate() == null ? null : t.getFeeRate().rateCardLabel(),
                t.getAmountCharged(),
                currency,
                replay);
    }

    public String getTicketNo() { return ticketNo; }
    public String getPlate() { return plate; }
    public VehicleType getVehicleType() { return vehicleType; }
    public String getSpotLabel() { return spotLabel; }
    public Integer getFloorNo() { return floorNo; }
    public Instant getEntryTime() { return entryTime; }
    public Instant getExitTime() { return exitTime; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public String getRateCard() { return rateCard; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public boolean isReplay() { return replay; }
}
