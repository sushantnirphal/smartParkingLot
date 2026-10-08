package com.parking.lot.web.dto;

import java.time.Instant;
import javax.validation.constraints.NotBlank;

/**
 * POST /api/v1/parking/exit (architecture §8).
 * {@code exitTime} is optional - when omitted the server clock is used (assumption A4).
 */
public class CheckOutRequest {

    @NotBlank(message = "ticketNo is required")
    private String ticketNo;

    private Instant exitTime;

    /** Idempotent retry: return the original receipt instead of 409 (contract rule C3). */
    private boolean replay;

    public CheckOutRequest() { }

    public CheckOutRequest(String ticketNo, Instant exitTime) {
        this.ticketNo = ticketNo;
        this.exitTime = exitTime;
    }

    public String getTicketNo() { return ticketNo; }
    public void setTicketNo(String ticketNo) { this.ticketNo = ticketNo; }
    public Instant getExitTime() { return exitTime; }
    public void setExitTime(Instant exitTime) { this.exitTime = exitTime; }
    public boolean isReplay() { return replay; }
    public void setReplay(boolean replay) { this.replay = replay; }
}
