package com.parking.lot.model;

/**
 * Lifecycle of a physical spot (architecture N6: BLOCKED covers maintenance, no soft delete).
 */
public enum SpotStatus {
    FREE,
    OCCUPIED,
    BLOCKED
}
