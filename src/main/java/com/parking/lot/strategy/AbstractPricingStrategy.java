package com.parking.lot.strategy;

import com.parking.lot.model.FeeRate;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Shared pricing rules for every {@link FeeStrategy} (architecture §6, rules R1-R4 / R7):
 *
 * <ol>
 *   <li><b>Grace</b> - a stay of {@code <= free_minutes} costs nothing; the comparison uses the
 *       <b>total</b> stay, not the billable part (R3).</li>
 *   <li><b>Ceiling</b> - partial billing blocks always round up ({@link #ceilDiv}).</li>
 *   <li><b>Cap</b> - {@code cap_per_day} caps the computed charge (resolution R4-1: applied as a
 *       session ceiling; multi-day scaling is the documented extension).</li>
 *   <li><b>Rounding</b> - HALF_UP to 2 decimals, applied once at the end (R2). {@code BigDecimal}
 *       only, never {@code double} (R7).</li>
 * </ol>
 */
public abstract class AbstractPricingStrategy implements FeeStrategy {

    @Override
    public final BigDecimal calculate(FeeContext context) {
        FeeRate rate = context.getRate();
        long stayMinutes = context.getStayMinutes();

        // R3: grace compares the total stay.
        if (stayMinutes <= rate.getFreeMinutes().longValue()) {
            return scale(BigDecimal.ZERO);
        }

        BigDecimal total = compute(rate, stayMinutes);

        BigDecimal cap = rate.getCapPerDay();
        if (cap != null && total.compareTo(cap) > 0) {
            total = cap;
        }
        return scale(total);
    }

    /**
     * @param stayMinutes total stay, guaranteed to be greater than the grace period
     * @return the uncapped charge for the stay
     */
    protected abstract BigDecimal compute(FeeRate rate, long stayMinutes);

    /** Ceiling division for billing blocks - partial blocks are chargeable in full. */
    protected static long ceilDiv(long value, long divisor) {
        if (divisor <= 0) {
            throw new IllegalArgumentException("billing block must be > 0, was " + divisor);
        }
        return (value + divisor - 1) / divisor;
    }

    /** Billable minutes after the grace period. */
    protected static long billableMinutes(FeeRate rate, long stayMinutes) {
        long billable = stayMinutes - rate.getFreeMinutes().longValue();
        return Math.max(0L, billable);
    }

    private static BigDecimal scale(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }
}
