package com.parking.lot.service;

import com.parking.lot.model.ParkingTicket;
import com.parking.lot.model.TicketStatus;
import com.parking.lot.repository.ParkingTicketRepository;
import com.parking.lot.web.dto.TicketDto;
import com.parking.lot.web.error.ApiException;
import com.parking.lot.web.error.ErrorCode;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read side for the ticket endpoints (§8 #4/#5).
 *
 * <p>{@code @Transactional(readOnly = true)} keeps the Hibernate session open while the DTO is
 * built, so lazy associations resolve here instead of relying on open-in-view (which is off).
 */
@Service
public class TicketQueryService {

    private final ParkingTicketRepository ticketRepository;

    public TicketQueryService(ParkingTicketRepository ticketRepository) {
        this.ticketRepository = ticketRepository;
    }

    @Transactional(readOnly = true)
    public TicketDto byTicketNo(String ticketNo) {
        String normalised = ticketNo == null ? "" : ticketNo.trim();
        ParkingTicket ticket = ticketRepository.findByTicketNo(normalised)
                .orElseThrow(() -> new ApiException(ErrorCode.TICKET_NOT_FOUND, "No ticket " + normalised));
        return TicketDto.from(ticket);
    }

    @Transactional(readOnly = true)
    public List<TicketDto> list(TicketStatus status, String plate, Pageable pageable) {
        String normalisedPlate = plate == null || plate.trim().isEmpty() ? null : plate.trim().toUpperCase();

        List<ParkingTicket> tickets;
        if (normalisedPlate != null) {
            tickets = status == null
                    ? ticketRepository.findByVehicle_PlateNoOrderByEntryTimeDesc(normalisedPlate, pageable)
                    : ticketRepository.findByVehicle_PlateNoAndStatusOrderByEntryTimeDesc(
                            normalisedPlate, status, pageable);
        } else if (status != null) {
            tickets = ticketRepository.findByStatusOrderByEntryTimeDesc(status, pageable);
        } else {
            tickets = ticketRepository.findAllByOrderByEntryTimeDesc(pageable);
        }

        List<TicketDto> result = new ArrayList<TicketDto>(tickets.size());
        for (ParkingTicket ticket : tickets) {
            result.add(TicketDto.from(ticket));
        }
        return result;
    }
}
