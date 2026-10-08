package com.parking.lot.strategy;

import com.parking.lot.model.FeeRate;
import com.parking.lot.model.VehicleType;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * Immutable input to every {@link FeeStrategy} (architecture P6 - built via the nested Builder
 * so future inputs never break the {@code calculate(FeeContext)} signature).
 */
public final class FeeContext {

    private final VehicleType vehicleType;
    private final LocalDateTime entryTime;
    private final LocalDateTime exitTime;
    private final FeeRate rate;

    private FeeContext(Builder b) {
        this.vehicleType = b.vehicleType;
        this.entryTime = b.entryTime;
        this.exitTime = b.exitTime;
        this.rate = b.rate;
    }

    /**
     * Stay length in whole minutes, floored (rule R1). Never negative - a reversed interval
     * yields 0 and is rejected earlier by the exit validation (rule R5).
     */
    public long getStayMinutes() {
        if (entryTime == null || exitTime == null || !exitTime.isAfter(entryTime)) {
            return 0L;
        }
        return Duration.between(entryTime, exitTime).toMinutes();
    }

    public VehicleType getVehicleType() { return vehicleType; }
    public LocalDateTime getEntryTime() { return entryTime; }
    public LocalDateTime getExitTime() { return exitTime; }
    public FeeRate getRate() { return rate; }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private VehicleType vehicleType;
        private LocalDateTime entryTime;
        private LocalDateTime exitTime;
        private FeeRate rate;

        public Builder vehicleType(VehicleType v) { this.vehicleType = v; return this; }
        public Builder entryTime(LocalDateTime t) { this.entryTime = t; return this; }
        public Builder exitTime(LocalDateTime t) { this.exitTime = t; return this; }
        public Builder rate(FeeRate r) { this.rate = r; return this; }

        public FeeContext build() {
            if (rate == null) {
                throw new IllegalArgumentException("FeeContext requires a rate snapshot (architecture N4)");
            }
            if (vehicleType == null) {
                vehicleType = rate.getVehicleType();
            }
            return new FeeContext(this);
        }
    }
}
