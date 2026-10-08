package com.parking.lot.web.error;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The uniform error envelope of architecture §8: {@code code}, {@code message},
 * {@code timestamp}, {@code traceId}. Serialized as {@code application/problem+json}.
 */
public class ErrorResponse {

    private final String code;
    private final String message;
    private final Instant timestamp;
    private final String traceId;
    private final int status;
    private final Map<String, Object> details;

    public ErrorResponse(String code, String message, Instant timestamp, String traceId,
                         int status, Map<String, Object> details) {
        this.code = code;
        this.message = message;
        this.timestamp = timestamp;
        this.traceId = traceId;
        this.status = status;
        this.details = details;
    }

    public String getCode() { return code; }
    public String getMessage() { return message; }
    public Instant getTimestamp() { return timestamp; }
    public String getTraceId() { return traceId; }
    public int getStatus() { return status; }
    public Map<String, Object> getDetails() { return details; }

    public static Map<String, Object> noDetails() {
        return new LinkedHashMap<String, Object>();
    }
}
