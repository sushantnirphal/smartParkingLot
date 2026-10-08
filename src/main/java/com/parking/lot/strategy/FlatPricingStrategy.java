package com.parking.lot.strategy;

import com.parking.lot.model.FeeRate;
import java.math.BigDecimal;

/**
 * FLAT - first block at the base price, then a flat step rate for whatever is left over.
 *
 * <pre>
 *   billable = stay - free_minutes
 *   total    = base_amount
 *            + step_amount * ceil((billable - base_duration_min) / step_duration_min)
 * </pre>
 *
 * <p>Reproduces the BUS row of the worked-example table in architecture §6 (90 min stay:
 * 80 + 40 = 120).
 */
public class FlatPricingStrategy extends AbstractPricingStrategy {

    @Override
    protected BigDecimal compute(FeeRate rate, long stayMinutes) {
        long billable = billableMinutes(rate, stayMinutes);
        long baseBlock = rate.getBaseDurationMin().longValue();
        BigDecimal total = rate.getBaseAmount();

        long remaining = billable - baseBlock;
        if (remaining > 0) {
            BigDecimal stepAmount = rate.getStepAmount();
            Integer stepDuration = rate.getStepDurationMin();
            if (stepAmount == null || stepDuration == null || stepDuration.intValue() <= 0) {
                throw new IllegalStateException(
                        "FLAT rate id=" + rate.getId() + " needs step_amount and step_duration_min");
            }
            long steps = ceilDiv(remaining, stepDuration.longValue());
            total = total.add(stepAmount.multiply(BigDecimal.valueOf(steps)));
        }
        return total;
    }
}
