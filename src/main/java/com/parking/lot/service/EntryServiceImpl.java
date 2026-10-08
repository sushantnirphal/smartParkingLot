package com.parking.lot.service;

import com.parking.lot.event.SpotChangeEvent;
import com.parking.lot.lock.LotLockManager;
import com.parking.lot.model.FeeRate;
import com.parking.lot.model.ParkingSpot;
import com.parking.lot.model.ParkingTicket;
import com.parking.lot.model.TicketStatus;
import com.parking.lot.model.Vehicle;
import com.parking.lot.model.VehicleType;
import com.parking.lot.repository.ParkingTicketRepository;
import com.parking.lot.repository.VehicleRepository;
import com.parking.lot.web.dto.CheckInRequest;
import com.parking.lot.web.dto.TicketDto;
import com.parking.lot.web.error.ApiException;
import com.parking.lot.web.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Check-in flow (architecture §5 two-stage selection + §7 layer 1 transaction boundary).
 *
 * <pre>
 *  validate no open ticket -> floor lock -> allocate -> persist ticket + occupy spot
 *  -> publish after-commit event -> release lock (on completion) -> 201
 * </pre>
 */
@Service
public class EntryServiceImpl implements EntryService {

    private final VehicleRepository vehicleRepository;
    private final ParkingTicketRepository ticketRepository;
    private final RateResolver rateResolver;
    private final AllocationService allocationService;
    private final TicketFactory ticketFactory;
    private final LotLockManager locks;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public EntryServiceImpl(VehicleRepository vehicleRepository,
                            ParkingTicketRepository ticketRepository,
                            RateResolver rateResolver,
                            AllocationService allocationService,
                            TicketFactory ticketFactory,
                            LotLockManager locks,
                            ApplicationEventPublisher events,
                            Clock clock) {
        this.vehicleRepository = vehicleRepository;
        this.ticketRepository = ticketRepository;
        this.rateResolver = rateResolver;
        this.allocationService = allocationService;
        this.ticketFactory = ticketFactory;
        this.locks = locks;
        this.events = events;
        this.clock = clock;
    }

    @Override
    @Transactional
    public TicketDto checkIn(CheckInRequest request) {
        final String plate = normalize(request.getPlate());
        final VehicleType type = request.getVehicleType();
        final int entryFloor = request.resolvedEntryFloor();

        // The floor lock covers the WHOLE use case and is released only after commit
        // (see LotLockManager): find/create, duplicate check, rate snapshot, allocation and
        // ticket insert are all serialised per floor, so a spot can never be handed out twice
        // and two gates on the same floor cannot double-register a plate (threat R1 / R3).
        return locks.withFloorLock(entryFloor, () -> {
            Vehicle vehicle = findOrCreate(plate, type);
            assertNotParked(vehicle);                            // authoritative re-check (R3)
            FeeRate rate = rateResolver.resolveRequired(type);   // R6: fail fast, never at exit

            ParkingSpot spot = allocationService.allocate(type, entryFloor);
            ParkingTicket ticket = ticketFactory.open(vehicle, spot, rate,
                    LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.SECONDS));
            ticketRepository.save(ticket);

            events.publishEvent(SpotChangeEvent.occupied(
                    spot.getId(), spot.getLabel(), spot.getFloor().getFloorNo(), spot.getSpotType()));
            return TicketDto.from(ticket);
        });
    }

    private Vehicle findOrCreate(String plate, VehicleType type) {
        Vehicle existing = vehicleRepository.findByPlateNo(plate).orElse(null);
        if (existing != null) {
            return existing;
        }
        // Flush immediately: the unique index on plate_no is the cross-floor backstop for
        // concurrent registration of the same vehicle (see RestExceptionHandler).
        return vehicleRepository.saveAndFlush(new Vehicle(plate, type));
    }

    private void assertNotParked(Vehicle vehicle) {
        if (ticketRepository.existsByVehicleIdAndStatus(vehicle.getId(), TicketStatus.OPEN)) {
            throw new ApiException(ErrorCode.ALREADY_PARKED,
                    "Vehicle " + vehicle.getPlateNo() + " already has an open ticket.");
        }
    }

    /** Contract rule C4: trim + upper case before every lookup. */
    static String normalize(String plate) {
        if (plate == null) {
            return null;
        }
        return plate.trim().toUpperCase();
    }
}
