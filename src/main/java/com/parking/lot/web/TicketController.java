package com.parking.lot.web;

import com.parking.lot.model.TicketStatus;
import com.parking.lot.service.TicketQueryService;
import com.parking.lot.web.dto.TicketDto;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Ticket queries (architecture §8, endpoints #4 and #5). Controllers stay thin (D2). */
@RestController
@RequestMapping("/api/v1/parking/tickets")
public class TicketController {

    /** Contract rule C8: default page size 20, hard maximum 100. */
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_PAGE_SIZE = 100;

    private final TicketQueryService ticketQueryService;

    public TicketController(TicketQueryService ticketQueryService) {
        this.ticketQueryService = ticketQueryService;
    }

    @GetMapping("/{ticketNo}")
    public TicketDto byTicketNo(@PathVariable("ticketNo") String ticketNo) {
        return ticketQueryService.byTicketNo(ticketNo);
    }

    @GetMapping
    public List<TicketDto> list(@RequestParam(name = "status", required = false) TicketStatus status,
                                @RequestParam(name = "plate", required = false) String plate,
                                @RequestParam(name = "page", defaultValue = "0") int page,
                                @RequestParam(name = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size) {
        Pageable pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, MAX_PAGE_SIZE)));
        return ticketQueryService.list(status, plate, pageable);
    }
}
