package com.parking.lot.service;

import com.parking.lot.web.dto.CheckInRequest;
import com.parking.lot.web.dto.TicketDto;

/** Use-case facade for the entry gate (architecture P5 / §8 endpoint #1). */
public interface EntryService {

    TicketDto checkIn(CheckInRequest request);
}
