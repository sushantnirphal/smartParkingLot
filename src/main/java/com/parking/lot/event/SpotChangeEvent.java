package com.parking.lot.event;

import com.parking.lot.model.SpotStatus;
import com.parking.lot.model.VehicleType;

/**
 * Observer payload (architecture P4). Published inside the use-case transaction and delivered
 * to listeners only after commit, so no consumer can ever see an uncommitted state.
 */
public class SpotChangeEvent {

    public enum Kind { OCCUPIED, FREED }

    private final Kind kind;
    private final long spotId;
    private final String label;
    private final int floorNo;
    private final VehicleType spotType;
    private final SpotStatus newStatus;

    private SpotChangeEvent(Kind kind, long spotId, String label, int floorNo,
                            VehicleType spotType, SpotStatus newStatus) {
        this.kind = kind;
        this.spotId = spotId;
        this.label = label;
        this.floorNo = floorNo;
        this.spotType = spotType;
        this.newStatus = newStatus;
    }

    public static SpotChangeEvent occupied(long spotId, String label, int floorNo, VehicleType type) {
        return new SpotChangeEvent(Kind.OCCUPIED, spotId, label, floorNo, type, SpotStatus.OCCUPIED);
    }

    public static SpotChangeEvent freed(long spotId, String label, int floorNo, VehicleType type) {
        return new SpotChangeEvent(Kind.FREED, spotId, label, floorNo, type, SpotStatus.FREE);
    }

    public Kind getKind() { return kind; }
    public long getSpotId() { return spotId; }
    public String getLabel() { return label; }
    public int getFloorNo() { return floorNo; }
    public VehicleType getSpotType() { return spotType; }
    public SpotStatus getNewStatus() { return newStatus; }
}
