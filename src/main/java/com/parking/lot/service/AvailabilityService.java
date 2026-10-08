package com.parking.lot.service;

import com.parking.lot.web.dto.AvailabilityDto;
import com.parking.lot.web.dto.SpotDto;
import com.parking.lot.model.SpotStatus;
import com.parking.lot.model.VehicleType;
import java.util.List;
import org.springframework.data.domain.Pageable;

/** Real-time availability (architecture G4 / §8 endpoints #3 and #6). */
public interface AvailabilityService {

    AvailabilityDto current();

    List<SpotDto> spotsOnFloor(int floorNo, SpotStatus status, VehicleType type, Pageable pageable);
}
