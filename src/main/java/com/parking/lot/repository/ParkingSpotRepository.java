package com.parking.lot.repository;

import com.parking.lot.model.ParkingSpot;
import com.parking.lot.model.SpotStatus;
import com.parking.lot.model.VehicleType;
import java.util.List;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParkingSpotRepository extends JpaRepository<ParkingSpot, Long> {

    /**
     * Stage 1 of the allocation algorithm (architecture §5) - served by
     * {@code idx_spot_lookup(floor_id, spot_type, status, position)}.
     */
    @Query("select s from ParkingSpot s "
            + "where s.floor.floorNo = :floorNo "
            + "and s.spotType = :type "
            + "and s.status = :status "
            + "order by s.position asc")
    List<ParkingSpot> findFreeByFloorAndType(@Param("floorNo") Integer floorNo,
                                             @Param("type") VehicleType type,
                                             @Param("status") SpotStatus status);

    /** Does the lot still have any usable space at all? (distinguishes the two 409 codes) */
    long countByStatus(SpotStatus status);

    /** Belt-and-braces row lock used during allocation (architecture §7 layer 3). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from ParkingSpot s where s.id = :id")
    Optional<ParkingSpot> findForUpdateById(@Param("id") Long id);

    /**
     * Real-time availability (A7 / N5): always derived, never a stored counter.
     * Returns rows of {floorNo, spotType, status, count}.
     */
    @Query("select s.floor.floorNo, s.spotType, s.status, count(s) "
            + "from ParkingSpot s group by s.floor.floorNo, s.spotType, s.status")
    List<Object[]> countByFloorTypeAndStatus();

    List<ParkingSpot> findByFloorFloorNoAndStatusOrderByPositionAsc(Integer floorNo, SpotStatus status);
}
