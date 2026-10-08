package com.parking.lot.web;

import com.parking.lot.service.EntryService;import com.parking.lot.service.FeeService;
import com.parking.lot.web.dto.CheckInRequest;
import com.parking.lot.web.dto.FeeQuoteDto;
import com.parking.lot.web.dto.FeeQuoteRequest;
import com.parking.lot.web.dto.TicketDto;
import javax.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Entry gate endpoints (architecture §8, endpoints #1 and #7). */
@RestController
@RequestMapping("/api/v1/parking")
public class EntryGateController {

    private final EntryService entryService;
    private final FeeService feeService;

    public EntryGateController(EntryService entryService, FeeService feeService) {
        this.entryService = entryService;
        this.feeService = feeService;
    }

    /** 201 Created with the ticket, or 409 ALREADY_PARKED / PARKING_FULL, 503 RATE_NOT_CONFIGURED. */
    @PostMapping("/entry")
    public ResponseEntity<TicketDto> checkIn(@Valid @RequestBody CheckInRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(entryService.checkIn(request));
    }

    /** Kiosk price preview - same arithmetic the exit gate will apply. */
    @PostMapping("/estimate-fee")
    public FeeQuoteDto estimateFee(@Valid @RequestBody FeeQuoteRequest request) {
        return feeService.quote(request);
    }
}
