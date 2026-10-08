package com.parking.lot.service;

import com.parking.lot.model.ParkingTicket;
import com.parking.lot.web.dto.ReceiptDto;
import java.time.LocalDateTime;

/** Use-case facade for the exit gate (architecture P5 / §8 endpoint #2). */
public interface ExitService {

    ReceiptDto checkOut(String ticketNo, LocalDateTime exitTime, boolean replay);
}
