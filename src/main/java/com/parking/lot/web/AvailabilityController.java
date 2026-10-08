package com.parking.lot.web;

import com.parking.lot.model.SpotStatus;
import com.parking.lot.model.VehicleType;
import com.parking.lot.service.AvailabilityService;
import com.parking.lot.web.dto.AvailabilityDto;
import com.parking.lot.web.dto.SpotDto;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Real-time availability (architecture §8, endpoints #3 and #6). */
@RestController
@RequestMapping("/api/v1")
public class AvailabilityController {

    private static final int MAX_PAGE_SIZE = 100;

    private final AvailabilityService availabilityService;

    public AvailabilityController(AvailabilityService availabilityService) {
        this.availabilityService = availabilityService;
    }

    @GetMapping("/availability")
    public AvailabilityDto availability() {
        return availabilityService.current();
    }

    @GetMapping("/floors/{floorNo}/spots")
    public List<SpotDto> spots(@PathVariable("floorNo") int floorNo,
                               @RequestParam(name = "status", defaultValue = "FREE") SpotStatus status,
                               @RequestParam(name = "type", required = false) VehicleType type,
                               @RequestParam(name = "page", defaultValue = "0") int page,
                               @RequestParam(name = "size", defaultValue = "50") int size) {
        int safeSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        Pageable pageable = PageRequest.of(Math.max(0, page), safeSize);
        return availabilityService.spotsOnFloor(floorNo, status, type, pageable);
    }
}
