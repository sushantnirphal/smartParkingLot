package com.parking.lot.web;

import com.parking.lot.service.ExitService;
import com.parking.lot.web.dto.CheckOutRequest;
import com.parking.lot.web.dto.ReceiptDto;
import javax.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Exit gate endpoint (architecture §8, endpoint #2) with idempotent replay (rule C3). */
@RestController
@RequestMapping("/api/v1/parking")
public class ExitGateController {

    private final ExitService exitService;

    public ExitGateController(ExitService exitService) {
        this.exitService = exitService;
    }

    @PostMapping("/exit")
    public ResponseEntity<ReceiptDto> checkOut(@Valid @RequestBody CheckOutRequest request,
                                               @RequestParam(name = "replay", defaultValue = "false") boolean replay) {
        boolean isReplay = replay || request.isReplay();
        return ResponseEntity.ok(
                exitService.checkOut(request.getTicketNo(), toUtc(request), isReplay));
    }

    private static java.time.LocalDateTime toUtc(CheckOutRequest request) {
        return request.getExitTime() == null
                ? null
                : java.time.LocalDateTime.ofInstant(request.getExitTime(), java.time.ZoneOffset.UTC);
    }
}
