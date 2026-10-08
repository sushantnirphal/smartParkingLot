package com.parking.lot.service;

import com.parking.lot.config.ParkingProperties;
import com.parking.lot.event.SpotChangeEvent;
import com.parking.lot.lock.LotLockManager;
import com.parking.lot.model.ParkingSpot;
import com.parking.lot.model.ParkingTicket;
import com.parking.lot.model.SpotStatus;
import com.parking.lot.model.TicketStatus;
import com.parking.lot.repository.ParkingSpotRepository;
import com.parking.lot.repository.ParkingTicketRepository;
import com.parking.lot.web.dto.ReceiptDto;
import com.parking.lot.web.error.ApiException;
import com.parking.lot.web.error.ErrorCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Check-out flow (architecture §7 layer 1):
 *
 * <pre>
 *  ticket lock -> SELECT ... FOR UPDATE -> validate -> fee -> close ticket + free spot
 *  (ONE transaction) -> after-commit event -> release lock
 * </pre>
 */
@Service
public class ExitServiceImpl implements ExitService {

    private final ParkingTicketRepository ticketRepository;
    private final ParkingSpotRepository spotRepository;
    private final FeeService feeService;
    private final LotLockManager locks;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final ParkingProperties properties;

    public ExitServiceImpl(ParkingTicketRepository ticketRepository,
                           ParkingSpotRepository spotRepository,
                           FeeService feeService,
                           LotLockManager locks,
                           ApplicationEventPublisher events,
                           Clock clock,
                           ParkingProperties properties) {
        this.ticketRepository = ticketRepository;
        this.spotRepository = spotRepository;
        this.feeService = feeService;
        this.locks = locks;
        this.events = events;
        this.clock = clock;
        this.properties = properties;
    }

    @Override
    @Transactional
    public ReceiptDto checkOut(String ticketNo, LocalDateTime exitTime, boolean replay) {
        final String ticketNoNorm = ticketNo == null ? "" : ticketNo.trim();
        // The ticket row is first touched INSIDE the lock. Reading it earlier would pin a
        // pre-commit snapshot in the Hibernate session, so a concurrent loser would act on the
        // stale OPEN state and trip the @Version column instead of hitting the idempotency check.
        return locks.withTicketLock(ticketNoNorm, () -> doCheckOut(ticketNoNorm, exitTime, replay));
    }

    private ReceiptDto doCheckOut(String ticketNo, LocalDateTime exitTime, boolean replay) {
        ParkingTicket ticket = ticketRepository.findForUpdateByTicketNo(ticketNo)
                .orElseThrow(() -> new ApiException(ErrorCode.TICKET_NOT_FOUND, "No ticket " + ticketNo));

        if (ticket.getStatus() == TicketStatus.CLOSED) {
            // Threat R4 / contract rule C3: a retried exit never charges twice.
            if (replay) {
                return ReceiptDto.from(ticket, properties.getCurrency(), true);
            }
            throw new ApiException(ErrorCode.TICKET_ALREADY_CLOSED,
                    "Ticket " + ticketNo + " was already closed at " + ticket.getExitTime());
        }
        if (ticket.getStatus() != TicketStatus.OPEN) {
            throw new ApiException(ErrorCode.TICKET_NOT_OPEN,
                    "Ticket " + ticketNo + " is " + ticket.getStatus());
        }

        // Whole-second resolution: the schema stores DATETIME (second precision), so anything
        // finer would be silently lost on reload and make the stored duration drift by a minute.
        LocalDateTime exit = (exitTime != null ? exitTime : LocalDateTime.now(clock))
                .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        // Rule R5: never a negative stay.
        if (exit.isBefore(ticket.getEntryTime())) {
            throw new ApiException(ErrorCode.INVALID_EXIT_TIME,
                    "exitTime " + exit + " is before entryTime " + ticket.getEntryTime());
        }

        int minutes = (int) Math.max(0, Duration.between(ticket.getEntryTime(), exit).toMinutes());
        BigDecimal fee = feeService.calculate(ticket, exit);

        ticket.close(exit, fee, minutes);
        ticketRepository.save(ticket);

        ParkingSpot spot = ticket.getSpot();
        if (spot != null) {
            ParkingSpot locked = spotRepository.findForUpdateById(spot.getId()).orElse(null);
            if (locked != null && locked.getStatus() == SpotStatus.OCCUPIED) {
                locked.setStatus(SpotStatus.FREE);
                spotRepository.save(locked);
            }
            events.publishEvent(SpotChangeEvent.freed(
                    spot.getId(), spot.getLabel(), spot.getFloor().getFloorNo(), spot.getSpotType()));
        }

        return ReceiptDto.from(ticket, properties.getCurrency(), false);
    }

    /** Convenience for callers that already hold a resolved exit instant. */
    public static LocalDateTime toLocalDateTime(java.time.Instant instant) {
        return instant == null ? null : LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
    }
}
