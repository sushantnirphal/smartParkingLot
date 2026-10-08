package com.parking.lot.web.dto;

import com.parking.lot.model.VehicleType;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * GET /api/v1/availability (architecture §8 #3).
 * Free/total counts are always derived from spot rows (assumption A7) - never a stored counter.
 */
public class AvailabilityDto {

    private final Instant asOf;
    private final long lotVersion;
    private final List<FloorAvailability> floors;

    public AvailabilityDto(Instant asOf, long lotVersion, List<FloorAvailability> floors) {
        this.asOf = asOf;
        this.lotVersion = lotVersion;
        this.floors = floors;
    }

    public Instant getAsOf() { return asOf; }
    public long getLotVersion() { return lotVersion; }
    public List<FloorAvailability> getFloors() { return floors; }

    /** One floor entry: {@code total} and {@code free} keyed by vehicle type. */
    public static class FloorAvailability {
        private final int floorNo;
        private final Map<VehicleType, Integer> total;
        private final Map<VehicleType, Integer> free;

        public FloorAvailability(int floorNo, Map<VehicleType, Integer> total, Map<VehicleType, Integer> free) {
            this.floorNo = floorNo;
            this.total = total;
            this.free = free;
        }

        public int getFloorNo() { return floorNo; }
        public Map<VehicleType, Integer> getTotal() { return total; }
        public Map<VehicleType, Integer> getFree() { return free; }
    }
}
