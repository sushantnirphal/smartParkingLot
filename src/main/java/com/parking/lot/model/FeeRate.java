package com.parking.lot.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

/**
 * A dated rate card row (architecture N3): changing a price inserts a new row, it never
 * rewrites history. The ticket keeps a reference to the row it was priced with (N4).
 *
 * <p>Column semantics (see architecture §6 for the worked examples):
 * <ul>
 *   <li>{@code freeMinutes} - grace period compared against the <b>total</b> stay (R3).</li>
 *   <li>{@code baseDurationMin} / {@code baseAmount} - the first billing block.</li>
 *   <li>{@code stepDurationMin} / {@code stepAmount} - subsequent blocks (FLAT strategy).</li>
 *   <li>{@code capPerDay} - ceiling applied to the computed charge (see resolution R4-1).</li>
 * </ul>
 */
@Entity
@Table(name = "fee_rate")
public class FeeRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 12)
    private VehicleType vehicleType;

    @Enumerated(EnumType.STRING)
    @Column(name = "strategy", nullable = false, length = 10)
    private PricingStrategyType strategy = PricingStrategyType.SLAB;

    @Column(name = "free_minutes", nullable = false)
    private Integer freeMinutes = 0;

    @Column(name = "base_duration_min", nullable = false)
    private Integer baseDurationMin;

    @Column(name = "base_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal baseAmount;

    @Column(name = "step_duration_min")
    private Integer stepDurationMin;

    @Column(name = "step_amount", precision = 10, scale = 2)
    private BigDecimal stepAmount;

    @Column(name = "cap_per_day", precision = 10, scale = 2)
    private BigDecimal capPerDay;

    @Column(name = "active", nullable = false)
    private Boolean active = Boolean.TRUE;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    protected FeeRate() {
        // JPA
    }

    /** Programmatic construction (tests, future admin API). */
    public FeeRate(VehicleType vehicleType, PricingStrategyType strategy, int freeMinutes,
                   int baseDurationMin, java.math.BigDecimal baseAmount,
                   Integer stepDurationMin, java.math.BigDecimal stepAmount,
                   java.math.BigDecimal capPerDay, java.time.LocalDate validFrom) {
        this.vehicleType = vehicleType;
        this.strategy = strategy;
        this.freeMinutes = freeMinutes;
        this.baseDurationMin = baseDurationMin;
        this.baseAmount = baseAmount;
        this.stepDurationMin = stepDurationMin;
        this.stepAmount = stepAmount;
        this.capPerDay = capPerDay;
        this.validFrom = validFrom;
        this.active = Boolean.TRUE;
    }

    public Integer getId() { return id; }
    public VehicleType getVehicleType() { return vehicleType; }
    public void setVehicleType(VehicleType vehicleType) { this.vehicleType = vehicleType; }
    public PricingStrategyType getStrategy() { return strategy; }
    public void setStrategy(PricingStrategyType strategy) { this.strategy = strategy; }
    public Integer getFreeMinutes() { return freeMinutes; }
    public void setFreeMinutes(Integer freeMinutes) { this.freeMinutes = freeMinutes; }
    public Integer getBaseDurationMin() { return baseDurationMin; }
    public void setBaseDurationMin(Integer baseDurationMin) { this.baseDurationMin = baseDurationMin; }
    public BigDecimal getBaseAmount() { return baseAmount; }
    public void setBaseAmount(BigDecimal baseAmount) { this.baseAmount = baseAmount; }
    public Integer getStepDurationMin() { return stepDurationMin; }
    public void setStepDurationMin(Integer stepDurationMin) { this.stepDurationMin = stepDurationMin; }
    public BigDecimal getStepAmount() { return stepAmount; }
    public void setStepAmount(BigDecimal stepAmount) { this.stepAmount = stepAmount; }
    public BigDecimal getCapPerDay() { return capPerDay; }
    public void setCapPerDay(BigDecimal capPerDay) { this.capPerDay = capPerDay; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public LocalDate getValidFrom() { return validFrom; }
    public void setValidFrom(LocalDate validFrom) { this.validFrom = validFrom; }

    /** Stable label echoed on receipts so a customer can see which card priced them. */
    public String rateCardLabel() {
        return vehicleType + "-" + strategy;
    }
}
