package com.parking.lot.model;

/**
 * Vehicle / spot size taxonomy (architecture A1).
 * The DB stores these as constrained VARCHAR(12) columns (risk RK4 decision).
 */
public enum VehicleType {
    MOTORCYCLE,
    CAR,
    BUS
}
