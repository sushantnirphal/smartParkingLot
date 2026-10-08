package com.parking.lot.service;

import com.parking.lot.model.ParkingFloor;
import com.parking.lot.model.ParkingSpot;
import com.parking.lot.model.SpotStatus;
import com.parking.lot.model.VehicleType;
import com.parking.lot.repository.ParkingFloorRepository;
import com.parking.lot.repository.ParkingSpotRepository;
import com.parking.lot.strategy.AllocationPolicy;
import com.parking.lot.web.error.ApiException;
import com.parking.lot.web.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stage 1 (indexed candidate query) + Stage 2 (in-memory policy pick) of the allocation
 * algorithm - architecture §5.
 *
 * <p>The method is {@code @Transactional} (D2) and additionally re-reads the chosen spot with
 * {@code SELECT ... FOR UPDATE} (architecture §7 layer 3), so a spot can never be handed to two
 * gates even if the JVM lock stripe ever collides or the process is scaled out.
 */
@Service
public class AllocationService {

    private final ParkingSpotRepository spotRepository;
    private final ParkingFloorRepository floorRepository;
    private final AllocationPolicy policy;

    public AllocationService(ParkingSpotRepository spotRepository,
                             ParkingFloorRepository floorRepository,
                             AllocationPolicy policy) {
        this.spotRepository = spotRepository;
        this.floorRepository = floorRepository;
        this.policy = policy;
    }

    /**
     * @return the occupied spot (persisted as OCCUPIED within the caller's transaction)
     * @throws ApiException {@code FLOOR_NOT_FOUND}, {@code PARKING_FULL} or
     *                      {@code PARKING_FULL_FOR_TYPE}
     */
    @Transactional
    public ParkingSpot allocate(VehicleType type, int entryFloor) {
        List<Integer> floors = floorNumbers();
        if (!floors.contains(Integer.valueOf(entryFloor))) {
            throw new ApiException(ErrorCode.FLOOR_NOT_FOUND, "No such floor: " + entryFloor);
        }

        for (Integer floorNo : policy.floorOrder(entryFloor, floors)) {
            List<ParkingSpot> candidates = spotRepository.findFreeByFloorAndType(floorNo, type, SpotStatus.FREE);
            Optional<ParkingSpot> pick = policy.select(candidates, type);
            if (pick.isPresent()) {
                ParkingSpot spot = lockAndOccupy(pick.get().getId());
                if (spot != null) {
                    return spot;
                }
                // Row changed underneath us (blocked / taken) - keep probing the remaining floors.
            }
        }

        if (spotRepository.countByStatus(SpotStatus.FREE) == 0L) {
            throw new ApiException(ErrorCode.PARKING_FULL, "The lot is full.");
        }
        throw new ApiException(ErrorCode.PARKING_FULL_FOR_TYPE,
                "No free " + type + " spot on any floor.");
    }

    /** Re-read under a row lock and flip FREE -> OCCUPIED; null when the row was no longer free. */
    private ParkingSpot lockAndOccupy(Long spotId) {
        ParkingSpot spot = spotRepository.findForUpdateById(spotId).orElse(null);
        if (spot == null || spot.getStatus() != SpotStatus.FREE) {
            return null;
        }
        spot.setStatus(SpotStatus.OCCUPIED);
        return spotRepository.save(spot);
    }

    private List<Integer> floorNumbers() {
        List<Integer> numbers = new ArrayList<Integer>();
        for (ParkingFloor floor : floorRepository.findAllByOrderByFloorNoAsc()) {
            numbers.add(floor.getFloorNo());
        }
        return numbers;
    }
}
