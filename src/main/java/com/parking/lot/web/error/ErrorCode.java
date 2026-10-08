package com.parking.lot.web.error;

import org.springframework.http.HttpStatus;

/**
 * Machine-readable, stable error codes (contract rule C1). Clients switch on {@code code};
 * the human text lives in the message.
 */
public enum ErrorCode {

    VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR"),
    INVALID_EXIT_TIME(HttpStatus.BAD_REQUEST, "INVALID_EXIT_TIME"),
    TICKET_NOT_OPEN(HttpStatus.BAD_REQUEST, "TICKET_NOT_OPEN"),

    ALREADY_PARKED(HttpStatus.CONFLICT, "ALREADY_PARKED"),
    PARKING_FULL(HttpStatus.CONFLICT, "PARKING_FULL"),
    PARKING_FULL_FOR_TYPE(HttpStatus.CONFLICT, "PARKING_FULL_FOR_TYPE"),
    TICKET_ALREADY_CLOSED(HttpStatus.CONFLICT, "TICKET_ALREADY_CLOSED"),

    TICKET_NOT_FOUND(HttpStatus.NOT_FOUND, "TICKET_NOT_FOUND"),
    FLOOR_NOT_FOUND(HttpStatus.NOT_FOUND, "FLOOR_NOT_FOUND"),

    RATE_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "RATE_NOT_CONFIGURED"),
    GATE_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "GATE_BUSY"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR");

    private final HttpStatus status;
    private final String code;

    ErrorCode(HttpStatus status, String code) {
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() { return status; }
    public String getCode() { return code; }
}
