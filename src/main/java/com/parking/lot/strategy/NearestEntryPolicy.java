package com.parking.lot.strategy;

import com.parking.lot.model.ParkingSpot;
import com.parking.lot.model.VehicleType;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Default policy: keep the vehicle on its entry floor, nearest to the entrance.
 *
 * <p>Complexity O(k) over the floor's free candidates (architecture §5). Ordering by
 * {@code position} is exactly architecture N2.
 */
public class NearestEntryPolicy implements AllocationPolicy {

    private final boolean allowMotorcycleOnCar;

    public NearestEntryPolicy(boolean allowMotorcycleOnCar) {
        this.allowMotorcycleOnCar = allowMotorcycleOnCar;
    }

    @Override
    public Optional<ParkingSpot> select(List<ParkingSpot> freeSpots, VehicleType type) {
        return AllocationPolicy.best(freeSpots, type, allowMotorcycleOnCar);
    }

    /** Entry floor first, then the remaining floors ascending as a fallback. */
    @Override
    public List<Integer> floorOrder(int entryFloor, List<Integer> allFloorNumbers) {
        List<Integer> order = new ArrayList<Integer>(allFloorNumbers.size());
        order.add(entryFloor);
        for (Integer floorNo : allFloorNumbers) {
            if (floorNo != null && floorNo.intValue() != entryFloor) {
                order.add(floorNo);
            }
        }
        return order;
    }
}
