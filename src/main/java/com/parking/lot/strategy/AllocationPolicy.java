package com.parking.lot.strategy;

import com.parking.lot.model.ParkingSpot;
import com.parking.lot.model.VehicleType;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Strategy seam for spot selection (architecture P1).
 *
 * <p>Implementations are pure functions over domain objects: no Spring, no repository,
 * no I/O (architecture D3), which keeps them directly unit-testable.
 *
 * <p>{@link #select(List, VehicleType)} chooses inside one floor; {@link #floorOrder(int, List)}
 * decides which floors are tried first, so the service can query floor by floor and stop early.
 */
public interface AllocationPolicy {

    /** Picks the preferred compatible spot from the candidates of a single floor. */
    Optional<ParkingSpot> select(List<ParkingSpot> freeSpots, VehicleType type);

    /**
     * Floors to probe in order for the given entry floor.
     * An empty list means "the caller's default ordering is already correct".
     */
    default List<Integer> floorOrder(int entryFloor, List<Integer> allFloorNumbers) {
        return Collections.emptyList();
    }

    /**
     * Compatibility rule shared by every policy: exact type match, plus the optional
     * "motorcycle may take a CAR spot" relaxation (architecture §5 vehicle-type rules).
     */
    static boolean matches(ParkingSpot spot, VehicleType type, boolean allowMotorcycleOnCar) {
        if (spot.getSpotType() == type) {
            return true;
        }
        return allowMotorcycleOnCar
                && type == VehicleType.MOTORCYCLE
                && spot.getSpotType() == VehicleType.CAR;
    }

    /**
     * Picks the preferred compatible spot: <b>always an exact-type spot first</b>, and only
     * falls back to the relaxed "motorcycle on a CAR spot" option when the floor has none.
     * Without this tiering the relaxation would greedily eat car capacity while motorcycle
     * bays stood empty - exactly the unpredictability assumption A1 is avoiding.
     */
    static Optional<ParkingSpot> best(List<ParkingSpot> freeSpots, VehicleType type,
                                      boolean allowMotorcycleOnCar) {
        if (freeSpots == null || freeSpots.isEmpty()) {
            return Optional.empty();
        }
        Comparator<ParkingSpot> byPosition = Comparator.comparingInt(ParkingSpot::getPosition);

        Optional<ParkingSpot> exact = freeSpots.stream()
                .filter(s -> s.getSpotType() == type)
                .min(byPosition);
        if (exact.isPresent()) {
            return exact;
        }
        if (allowMotorcycleOnCar && type == VehicleType.MOTORCYCLE) {
            return freeSpots.stream()
                    .filter(s -> s.getSpotType() == VehicleType.CAR)
                    .min(byPosition);
        }
        return Optional.empty();
    }
}
