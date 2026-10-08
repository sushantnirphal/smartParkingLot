package com.parking.lot.strategy;

import static org.assertj.core.api.Assertions.assertThat;

import com.parking.lot.model.ParkingFloor;
import com.parking.lot.model.ParkingSpot;
import com.parking.lot.model.VehicleType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * Task C6 - allocation policy unit suite (no Spring, no DB: architecture D3).
 * Locks down the compatibility matrix and the "nearest" / "lowest floor" ordering.
 */
class AllocationPolicyTest {

    private static ParkingSpot spot(int position, VehicleType type) {
        return new ParkingSpot(new ParkingFloor(1), "X" + position, type, position);
    }

    // ---------------------------------------------------------------- selection

    @Test
    void nearestEntryPicksLowestPositionOnTheFloor() {
        NearestEntryPolicy policy = new NearestEntryPolicy(false);
        List<ParkingSpot> candidates = new ArrayList<ParkingSpot>(Arrays.asList(
                spot(7, VehicleType.CAR), spot(3, VehicleType.CAR), spot(5, VehicleType.CAR)));

        Optional<ParkingSpot> pick = policy.select(candidates, VehicleType.CAR);

        assertThat(pick).isPresent();
        assertThat(pick.get().getPosition()).isEqualTo(3);
    }

    @Test
    void emptyCandidateListYieldsEmptyOptional() {
        NearestEntryPolicy policy = new NearestEntryPolicy(false);
        assertThat(policy.select(Collections.<ParkingSpot>emptyList(), VehicleType.CAR)).isEmpty();
        assertThat(policy.select(null, VehicleType.CAR)).isEmpty();
    }

    @Test
    void wrongTypeSpotsAreNeverSelected() {
        NearestEntryPolicy policy = new NearestEntryPolicy(false);
        List<ParkingSpot> candidates = Arrays.asList(
                spot(1, VehicleType.MOTORCYCLE), spot(2, VehicleType.MOTORCYCLE));

        assertThat(policy.select(candidates, VehicleType.CAR)).isEmpty();
        assertThat(policy.select(candidates, VehicleType.BUS)).isEmpty();
    }

    @Test
    void busNeverTakesACarSpotEvenWhenCarsAreFree() {
        NearestEntryPolicy policy = new NearestEntryPolicy(false);
        List<ParkingSpot> candidates = Arrays.asList(spot(1, VehicleType.CAR), spot(2, VehicleType.CAR));

        assertThat(policy.select(candidates, VehicleType.BUS)).isEmpty();
    }

    @Test
    void motorcycleMayOnlyUseCarSpotsWhenTheRelaxationIsEnabled() {
        List<ParkingSpot> candidates = Arrays.asList(spot(1, VehicleType.CAR));

        assertThat(new NearestEntryPolicy(false).select(candidates, VehicleType.MOTORCYCLE)).isEmpty();
        assertThat(new NearestEntryPolicy(true).select(candidates, VehicleType.MOTORCYCLE)).isPresent();
    }

    @Test
    void exactMatchAlwaysWinsOverTheRelaxation() {
        NearestEntryPolicy permissive = new NearestEntryPolicy(true);
        List<ParkingSpot> candidates = Arrays.asList(spot(1, VehicleType.CAR), spot(9, VehicleType.MOTORCYCLE));

        Optional<ParkingSpot> pick = permissive.select(candidates, VehicleType.MOTORCYCLE);

        assertThat(pick).isPresent();
        assertThat(pick.get().getPosition()).isEqualTo(9);
    }

    @Test
    void entityCompatibilityIsExactTypeMatchingOnly() {
        assertThat(spot(1, VehicleType.CAR).isCompatible(VehicleType.CAR)).isTrue();
        assertThat(spot(1, VehicleType.CAR).isCompatible(VehicleType.MOTORCYCLE)).isFalse();
        assertThat(spot(1, VehicleType.BUS).isCompatible(VehicleType.CAR)).isFalse();
    }

    // ------------------------------------------------------------- floor ordering

    @Test
    void nearestEntryTriesTheEntryFloorFirstThenTheOthersAscending() {
        NearestEntryPolicy policy = new NearestEntryPolicy(false);

        assertThat(policy.floorOrder(2, Arrays.asList(1, 2, 3))).containsExactly(2, 1, 3);
        assertThat(policy.floorOrder(1, Arrays.asList(1, 2, 3))).containsExactly(1, 2, 3);
        assertThat(policy.floorOrder(9, Arrays.asList(1, 2, 3))).containsExactly(9, 1, 2, 3);
    }

    @Test
    void lowestFloorAlwaysScansAscendingRegardlessOfEntryFloor() {
        LowestFloorPolicy policy = new LowestFloorPolicy(false);

        assertThat(policy.floorOrder(3, Arrays.asList(1, 2, 3))).containsExactly(1, 2, 3);
        assertThat(policy.floorOrder(1, Arrays.asList(1, 2, 3))).containsExactly(1, 2, 3);
    }

    @Test
    void lowestFloorSelectsTheFirstCompatibleSpotOfTheProbedFloor() {
        LowestFloorPolicy policy = new LowestFloorPolicy(false);
        List<ParkingSpot> candidates = Arrays.asList(spot(4, VehicleType.CAR), spot(2, VehicleType.CAR));

        assertThat(policy.select(candidates, VehicleType.CAR)).hasValueSatisfying(
                s -> assertThat(s.getPosition()).isEqualTo(2));
    }
}
