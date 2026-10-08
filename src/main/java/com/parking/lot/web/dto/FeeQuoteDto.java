package com.parking.lot.web.dto;

import com.parking.lot.model.VehicleType;
import java.math.BigDecimal;

/** Quote response: same arithmetic the exit gate will apply, priced on today's card. */
public class FeeQuoteDto {

    private final VehicleType vehicleType;
    private final int durationMinutes;
    private final BigDecimal amount;
    private final String currency;
    private final String rateCard;
    private final String strategy;

    public FeeQuoteDto(VehicleType vehicleType, int durationMinutes, BigDecimal amount,
                       String currency, String rateCard, String strategy) {
        this.vehicleType = vehicleType;
        this.durationMinutes = durationMinutes;
        this.amount = amount;
        this.currency = currency;
        this.rateCard = rateCard;
        this.strategy = strategy;
    }

    public VehicleType getVehicleType() { return vehicleType; }
    public int getDurationMinutes() { return durationMinutes; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getRateCard() { return rateCard; }
    public String getStrategy() { return strategy; }
}
