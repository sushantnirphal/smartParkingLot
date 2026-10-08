package com.parking.lot.web.dto;

import com.parking.lot.model.VehicleType;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

/** POST /api/v1/parking/entry (architecture §8). */
public class CheckInRequest {

    @NotBlank(message = "plate is required")
    @Size(max = 20, message = "plate must be at most 20 characters")
    private String plate;

    @NotNull(message = "vehicleType is required")
    private VehicleType vehicleType;

    /** Optional - defaults to floor 1 (the lot entrance). */
    private Integer entryFloor;

    public CheckInRequest() { }

    public CheckInRequest(String plate, VehicleType vehicleType, Integer entryFloor) {
        this.plate = plate;
        this.vehicleType = vehicleType;
        this.entryFloor = entryFloor;
    }

    public String getPlate() { return plate; }
    public void setPlate(String plate) { this.plate = plate; }
    public VehicleType getVehicleType() { return vehicleType; }
    public void setVehicleType(VehicleType vehicleType) { this.vehicleType = vehicleType; }
    public Integer getEntryFloor() { return entryFloor; }
    public void setEntryFloor(Integer entryFloor) { this.entryFloor = entryFloor; }

    /** Normalised entry floor (assumption: floor 1 when the gate does not report one). */
    public int resolvedEntryFloor() {
        return entryFloor == null ? 1 : entryFloor.intValue();
    }
}
