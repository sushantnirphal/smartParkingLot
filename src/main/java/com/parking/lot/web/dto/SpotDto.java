package com.parking.lot.web.dto;

import com.parking.lot.model.ParkingSpot;
import com.parking.lot.model.SpotStatus;
import com.parking.lot.model.VehicleType;

/** Raw spot projection for ops/debug endpoint #6 (architecture §8). */
public class SpotDto {

    private final String label;
    private final Integer floorNo;
    private final VehicleType spotType;
    private final SpotStatus status;
    private final Integer position;

    public SpotDto(String label, Integer floorNo, VehicleType spotType, SpotStatus status, Integer position) {
        this.label = label;
        this.floorNo = floorNo;
        this.spotType = spotType;
        this.status = status;
        this.position = position;
    }

    public static SpotDto from(ParkingSpot s) {
        return new SpotDto(
                s.getLabel(),
                s.getFloor() == null ? null : s.getFloor().getFloorNo(),
                s.getSpotType(),
                s.getStatus(),
                s.getPosition());
    }

    public String getLabel() { return label; }
    public Integer getFloorNo() { return floorNo; }
    public VehicleType getSpotType() { return spotType; }
    public SpotStatus getStatus() { return status; }
    public Integer getPosition() { return position; }
}
