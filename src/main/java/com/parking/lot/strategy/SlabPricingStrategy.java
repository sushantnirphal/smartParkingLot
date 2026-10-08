package com.parking.lot.strategy;

import com.parking.lot.model.FeeRate;
import java.math.BigDecimal;

/**
 * SLAB - pure block pricing.
 *
 * <pre>
 *   billable = stay - free_minutes
 *   total    = base_amount * ceil(billable / base_duration_min)
 * </pre>
 *
 * <p>This is the model that reproduces the MOTORCYCLE and CAR rows of the worked-example table
 * in architecture §6 (see "Implementation resolutions"). Partial blocks are charged in full,
 * which is the standard car-park convention.
 */
public class SlabPricingStrategy extends AbstractPricingStrategy {

    @Override
    protected BigDecimal compute(FeeRate rate, long stayMinutes) {
        long billable = billableMinutes(rate, stayMinutes);
        long blocks = ceilDiv(billable, rate.getBaseDurationMin().longValue());
        return rate.getBaseAmount().multiply(BigDecimal.valueOf(blocks));
    }
}
