package com.parking.lot.repository;

import com.parking.lot.model.ParkingFloor;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ParkingFloorRepository extends JpaRepository<ParkingFloor, Integer> {

    Optional<ParkingFloor> findByFloorNo(Integer floorNo);

    List<ParkingFloor> findAllByOrderByFloorNoAsc();
}
