package com.parking.lot.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised parking configuration (architecture D1: strategies are swappable via config).
 *
 * <pre>
 * parking:
 *   allocation-policy: nearest-entry   # nearest-entry | lowest-floor
 *   pricing-strategy:  ""              # "" = follow fee_rate.strategy (data driven, N3)
 *                                      # slab | flat forces one strategy (ops override)
 *   allow-motorcycle-on-car: false     # architecture §5 vehicle-type rules
 *   lock-timeout-ms: 2000              # architecture §7 tryLock timeout -> 503 GATE_BUSY
 *   currency: INR
 * </pre>
 */
@ConfigurationProperties(prefix = "parking")
public class ParkingProperties {

    /** Active {@code AllocationPolicy}: {@code nearest-entry} or {@code lowest-floor}. */
    private String allocationPolicy = "nearest-entry";

    /** Optional global pricing override; empty means "use the strategy on the fee_rate row". */
    private String pricingStrategy = "";

    /** When true a MOTORCYCLE may take a CAR spot (architecture §5, default off = deterministic). */
    private boolean allowMotorcycleOnCar = false;

    /** Lock acquisition timeout before a gate degrades to 503 GATE_BUSY (architecture §7). */
    private long lockTimeoutMs = 2000L;

    /** Currency returned on receipts (contract rule C6). */
    private String currency = "INR";

    public String getAllocationPolicy() { return allocationPolicy; }
    public void setAllocationPolicy(String allocationPolicy) { this.allocationPolicy = allocationPolicy; }

    public String getPricingStrategy() { return pricingStrategy; }
    public void setPricingStrategy(String pricingStrategy) { this.pricingStrategy = pricingStrategy; }

    public boolean isAllowMotorcycleOnCar() { return allowMotorcycleOnCar; }
    public void setAllowMotorcycleOnCar(boolean allowMotorcycleOnCar) { this.allowMotorcycleOnCar = allowMotorcycleOnCar; }

    public long getLockTimeoutMs() { return lockTimeoutMs; }
    public void setLockTimeoutMs(long lockTimeoutMs) { this.lockTimeoutMs = lockTimeoutMs; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
}
