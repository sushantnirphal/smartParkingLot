package com.parking.lot.service;

import com.parking.lot.cache.AvailabilityCache;
import com.parking.lot.model.SpotStatus;
import com.parking.lot.model.VehicleType;
import com.parking.lot.repository.ParkingFloorRepository;
import com.parking.lot.repository.ParkingSpotRepository;
import com.parking.lot.web.dto.AvailabilityDto;
import com.parking.lot.web.dto.SpotDto;
import com.parking.lot.web.error.ApiException;
import com.parking.lot.web.error.ErrorCode;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-through availability (architecture D4 / A7).
 *
 * <p>Counts are always derived from spot rows ({@code GROUP BY floor, type, status}) so they can
 * never drift; the cache only avoids recomputing the same aggregate repeatedly and is dropped by
 * the after-commit listener whenever a spot changes.
 */
@Service
public class AvailabilityServiceImpl implements AvailabilityService {

    private final ParkingSpotRepository spotRepository;
    private final ParkingFloorRepository floorRepository;
    private final AvailabilityCache cache;
    private final Clock clock;

    public AvailabilityServiceImpl(ParkingSpotRepository spotRepository,
                                   ParkingFloorRepository floorRepository,
                                   AvailabilityCache cache,
                                   Clock clock) {
        this.spotRepository = spotRepository;
        this.floorRepository = floorRepository;
        this.cache = cache;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public AvailabilityDto current() {
        return cache.getOrCompute(this::load);
    }

    private AvailabilityDto load() {
        List<Object[]> rows = spotRepository.countByFloorTypeAndStatus();

        Map<Integer, Map<VehicleType, Integer>> total = new TreeMap<Integer, Map<VehicleType, Integer>>();
        Map<Integer, Map<VehicleType, Integer>> free = new TreeMap<Integer, Map<VehicleType, Integer>>();

        for (Object[] row : rows) {
            Integer floorNo = (Integer) row[0];
            VehicleType type = (VehicleType) row[1];
            SpotStatus status = (SpotStatus) row[2];
            Long count = (Long) row[3];

            Map<VehicleType, Integer> totalsForFloor =
                    total.computeIfAbsent(floorNo, k -> zeroed());
            Map<VehicleType, Integer> freeForFloor =
                    free.computeIfAbsent(floorNo, k -> zeroed());

            int current = totalsForFloor.get(type).intValue();
            totalsForFloor.put(type, current + count.intValue());
            if (status == SpotStatus.FREE) {
                freeForFloor.put(type, freeForFloor.get(type).intValue() + count.intValue());
            }
        }

        List<AvailabilityDto.FloorAvailability> floors = new ArrayList<AvailabilityDto.FloorAvailability>();
        for (Map.Entry<Integer, Map<VehicleType, Integer>> entry : total.entrySet()) {
            floors.add(new AvailabilityDto.FloorAvailability(
                    entry.getKey().intValue(), entry.getValue(), free.get(entry.getKey())));
        }
        return new AvailabilityDto(Instant.now(clock), cache.currentVersion(), floors);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SpotDto> spotsOnFloor(int floorNo, SpotStatus status, VehicleType type, Pageable pageable) {
        if (floorRepository.findByFloorNo(Integer.valueOf(floorNo)).orElse(null) == null) {
            throw new ApiException(ErrorCode.FLOOR_NOT_FOUND, "No such floor: " + floorNo);
        }
        List<SpotDto> result = new ArrayList<SpotDto>();
        for (com.parking.lot.model.ParkingSpot spot
                : spotRepository.findByFloorFloorNoAndStatusOrderByPositionAsc(Integer.valueOf(floorNo), status)) {
            if (type != null && spot.getSpotType() != type) {
                continue;
            }
            result.add(SpotDto.from(spot));
        }
        int from = Math.min(pageable.getOffset() > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) pageable.getOffset(),
                result.size());
        int to = Math.min(from + pageable.getPageSize(), result.size());
        return result.subList(from, to);
    }

    private static Map<VehicleType, Integer> zeroed() {
        Map<VehicleType, Integer> map = new EnumMap<VehicleType, Integer>(VehicleType.class);
        for (VehicleType type : VehicleType.values()) {
            map.put(type, Integer.valueOf(0));
        }
        return map;
    }
}
