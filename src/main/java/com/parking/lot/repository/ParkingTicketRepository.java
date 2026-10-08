package com.parking.lot.repository;

import com.parking.lot.model.ParkingTicket;
import com.parking.lot.model.TicketStatus;
import java.util.List;
import java.util.Optional;
import javax.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ParkingTicketRepository extends JpaRepository<ParkingTicket, Long> {

    Optional<ParkingTicket> findByTicketNo(String ticketNo);

    /** Assumption A3 / threat R3: at most one open ticket per vehicle. */
    boolean existsByVehicleIdAndStatus(Long vehicleId, TicketStatus status);

    Optional<ParkingTicket> findByVehicleIdAndStatus(Long vehicleId, TicketStatus status);

    /** Lookup by plate (endpoint #5): walks the vehicle association. */
    List<ParkingTicket> findByVehicle_PlateNoOrderByEntryTimeDesc(String plateNo, Pageable pageable);

    List<ParkingTicket> findByVehicle_PlateNoAndStatusOrderByEntryTimeDesc(String plateNo, TicketStatus status,
                                                                           Pageable pageable);

    /** Threat R4: the check-out path locks the row before it reads any state. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ParkingTicket t where t.id = :id")
    Optional<ParkingTicket> findForUpdateById(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from ParkingTicket t where t.ticketNo = :ticketNo")
    Optional<ParkingTicket> findForUpdateByTicketNo(@Param("ticketNo") String ticketNo);

    List<ParkingTicket> findByStatusOrderByEntryTimeDesc(TicketStatus status, Pageable pageable);

    List<ParkingTicket> findAllByOrderByEntryTimeDesc(Pageable pageable);

    /**
     * Integrity probe used by the concurrency suite (G5): resolves in the database so it works
     * on detached entities. Two tickets on the same spot would collapse to a smaller list.
     */
    @Query("select distinct t.spot.id from ParkingTicket t where t.status = :status")
    List<Long> findDistinctSpotIdsByStatus(@Param("status") TicketStatus status);
}
