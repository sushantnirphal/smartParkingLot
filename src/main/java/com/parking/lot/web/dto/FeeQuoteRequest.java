package com.parking.lot.web.dto;

import com.parking.lot.model.VehicleType;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

/** POST /api/v1/parking/estimate-fee (architecture §8 #7) - kiosk price preview. */
public class FeeQuoteRequest {

    @NotNull(message = "vehicleType is required")
    private VehicleType vehicleType;

    @NotNull(message = "durationMinutes is required")
    @Min(value = 0, message = "durationMinutes must not be negative")
    private Integer durationMinutes;

    public FeeQuoteRequest() { }

    public FeeQuoteRequest(VehicleType vehicleType, Integer durationMinutes) {
        this.vehicleType = vehicleType;
        this.durationMinutes = durationMinutes;
    }

    public VehicleType getVehicleType() { return vehicleType; }
    public void setVehicleType(VehicleType vehicleType) { this.vehicleType = vehicleType; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public void setDurationMinutes(Integer durationMinutes) { this.durationMinutes = durationMinutes; }
}
