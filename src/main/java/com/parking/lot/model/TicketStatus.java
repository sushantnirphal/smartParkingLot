package com.parking.lot.model;

/**
 * Ticket lifecycle (architecture N1). The status column is also the idempotency
 * signal for check-in / check-out retries (architecture §7).
 */
public enum TicketStatus {
    OPEN,
    CLOSED,
    CANCELLED
}
