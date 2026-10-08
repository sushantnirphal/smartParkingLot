package com.parking.lot.strategy;

import java.math.BigDecimal;

/**
 * Strategy seam for pricing (architecture P1 / §6).
 * Implementations are pure and side-effect free (architecture D3).
 */
public interface FeeStrategy {

    /**
     * @return the charge rounded HALF_UP to 2 decimals (rule R2). Never {@code null};
     *         {@code BigDecimal.ZERO} for stays inside the grace period.
     */
    BigDecimal calculate(FeeContext context);
}
