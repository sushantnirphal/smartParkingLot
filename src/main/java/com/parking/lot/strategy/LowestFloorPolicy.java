package com.parking.lot.strategy;

import com.parking.lot.model.ParkingSpot;
import com.parking.lot.model.VehicleType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Fills the lowest floors first, keeping upper floors free for large vehicles.
 * Worst case O(F) candidate queries (architecture §5 policy table), stopping at the first hit.
 */
public class LowestFloorPolicy implements AllocationPolicy {

    private final boolean allowMotorcycleOnCar;

    public LowestFloorPolicy(boolean allowMotorcycleOnCar) {
        this.allowMotorcycleOnCar = allowMotorcycleOnCar;
    }

    @Override
    public Optional<ParkingSpot> select(List<ParkingSpot> freeSpots, VehicleType type) {
        return AllocationPolicy.best(freeSpots, type, allowMotorcycleOnCar);
    }

    @Override
    public List<Integer> floorOrder(int entryFloor, List<Integer> allFloorNumbers) {
        List<Integer> order = new ArrayList<Integer>(allFloorNumbers);
        Collections.sort(order);
        return order;
    }
}
