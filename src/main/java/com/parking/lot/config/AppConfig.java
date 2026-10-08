package com.parking.lot.config;

import com.parking.lot.model.FeeRate;
import com.parking.lot.model.PricingStrategyType;
import com.parking.lot.strategy.AllocationPolicy;
import com.parking.lot.strategy.FeeStrategy;
import com.parking.lot.strategy.FlatPricingStrategy;
import com.parking.lot.strategy.LowestFloorPolicy;
import com.parking.lot.strategy.NearestEntryPolicy;
import com.parking.lot.strategy.SlabPricingStrategy;
import java.time.Clock;
import java.util.EnumMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the two Strategy seams (architecture P1 / D1).
 *
 * <p>The concrete strategies are deliberately <b>not</b> annotated components: they stay
 * Spring-free so they can be unit tested as plain objects (architecture D3).
 */
@Configuration
public class AppConfig {

    /** Single clock source for the whole application (assumption A4, contract rule C5). */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Selects the allocation policy from {@code parking.allocation-policy}.
     * Both policies are instantiated here rather than as beans to avoid by-type ambiguity.
     */
    @Bean
    public AllocationPolicy allocationPolicy(ParkingProperties properties) {
        if ("lowest-floor".equalsIgnoreCase(properties.getAllocationPolicy())) {
            return new LowestFloorPolicy(properties.isAllowMotorcycleOnCar());
        }
        return new NearestEntryPolicy(properties.isAllowMotorcycleOnCar());
    }

    /**
     * Pricing registry keyed by the strategy stored on the {@code fee_rate} row (data driven, N3).
     * {@code parking.pricing-strategy} acts as an optional global ops override (D1).
     */
    @Bean
    public Map<PricingStrategyType, FeeStrategy> feeStrategies(ParkingProperties properties) {
        SlabPricingStrategy slab = new SlabPricingStrategy();
        FlatPricingStrategy flat = new FlatPricingStrategy();

        Map<PricingStrategyType, FeeStrategy> strategies =
                new EnumMap<PricingStrategyType, FeeStrategy>(PricingStrategyType.class);
        strategies.put(PricingStrategyType.SLAB, slab);
        strategies.put(PricingStrategyType.FLAT, flat);

        // An explicit config value shadows the per-rate strategy; blank keeps it data driven.
        PricingStrategyType forced = parse(properties.getPricingStrategy());
        if (forced != null) {
            strategies.put(PricingStrategyType.SLAB, strategies.get(forced));
            strategies.put(PricingStrategyType.FLAT, strategies.get(forced));
        }
        return strategies;
    }

    private static PricingStrategyType parse(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        return PricingStrategyType.valueOf(raw.trim().toUpperCase());
    }

    /** Convenience lookup used by {@code FeeService}. */
    public static FeeStrategy resolve(Map<PricingStrategyType, FeeStrategy> strategies, FeeRate rate) {
        FeeStrategy strategy = strategies.get(rate.getStrategy());
        if (strategy == null) {
            throw new IllegalStateException("No FeeStrategy registered for " + rate.getStrategy());
        }
        return strategy;
    }
}
