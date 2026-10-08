package com.parking.lot.repository;

import com.parking.lot.model.FeeRate;
import com.parking.lot.model.VehicleType;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeRateRepository extends JpaRepository<FeeRate, Integer> {

    /**
     * Rate selection at check-in (architecture N3 / rule R6): the newest active card whose
     * {@code valid_from} is not in the future. Append-only - never updated in place (N4).
     */
    Optional<FeeRate> findTop1ByVehicleTypeAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDesc(
            VehicleType vehicleType, LocalDate onDate);
}
