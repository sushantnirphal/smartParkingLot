package com.parking.lot.repository;

import com.parking.lot.model.Vehicle;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VehicleRepository extends JpaRepository<Vehicle, Long> {

    /** Plate is normalised to upper case before every lookup (contract rule C4). */
    Optional<Vehicle> findByPlateNo(String plateNo);

    boolean existsByPlateNo(String plateNo);
}
