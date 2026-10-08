package com.parking.lot.model;

/**
 * Pricing algorithm stored on {@code fee_rate.strategy} (architecture N3: pricing is data).
 */
public enum PricingStrategyType {
    /** Pure block pricing: {@code base * ceil(billable / base_duration_min)}. */
    SLAB,
    /** Base block plus a flat step rate: {@code base + step * ceil(remaining / step_duration_min)}. */
    FLAT
}
