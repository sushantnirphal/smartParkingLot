package com.parking.lot.service;

import com.parking.lot.model.ParkingTicket;
import com.parking.lot.web.dto.FeeQuoteDto;
import com.parking.lot.web.dto.FeeQuoteRequest;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Fee computation seam used by the exit gate and the kiosk quote endpoint (architecture §8). */
public interface FeeService {

    /** Prices an open ticket for the given exit instant using its rate snapshot (N4). */
    BigDecimal calculate(ParkingTicket ticket, LocalDateTime exitTime);

    /** Prices a ticket at its own recorded exit time. */
    BigDecimal calculate(ParkingTicket ticket);

    /** Live quote against today's card (endpoint #7) - no ticket involved. */
    FeeQuoteDto quote(FeeQuoteRequest request);
}
