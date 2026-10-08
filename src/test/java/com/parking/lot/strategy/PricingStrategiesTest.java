package com.parking.lot.strategy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.parking.lot.model.FeeRate;
import com.parking.lot.model.PricingStrategyType;
import com.parking.lot.model.VehicleType;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/**
 * Task D6 - table-driven pricing suite.
 *
 * <p>Every row of the worked-example table in architecture §6 is a fixture here, plus the
 * grace / block / cap boundaries and the rounding rules R2-R4.
 */
class PricingStrategiesTest {

    // ---- rate cards exactly as seeded by V2__seed_rates.sql ------------------
    private static final FeeRate CAR = rate(VehicleType.CAR, PricingStrategyType.SLAB,
            30, 120, "40.00", 60, "20.00", "200.00");
    private static final FeeRate MOTORCYCLE = rate(VehicleType.MOTORCYCLE, PricingStrategyType.SLAB,
            15, 60, "10.00", 30, "5.00", "60.00");
    private static final FeeRate BUS = rate(VehicleType.BUS, PricingStrategyType.FLAT,
            0, 60, "80.00", 60, "40.00", "500.00");

    private static final FeeStrategy SLAB = new SlabPricingStrategy();
    private static final FeeStrategy FLAT = new FlatPricingStrategy();

    private static FeeRate rate(VehicleType type, PricingStrategyType strategy, int free,
                                int baseMin, String baseAmount, int stepMin, String stepAmount, String cap) {
        return new FeeRate(type, strategy, free, baseMin, new BigDecimal(baseAmount),
                Integer.valueOf(stepMin), new BigDecimal(stepAmount), new BigDecimal(cap),
                LocalDate.of(2026, 1, 1));
    }

    private static FeeContext stay(FeeRate rate, long minutes) {
        LocalDateTime entry = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        return FeeContext.builder()
                .rate(rate)
                .entryTime(entry)
                .exitTime(entry.plusMinutes(minutes))
                .build();
    }

    private static FeeStrategy strategyFor(FeeRate rate) {
        return rate.getStrategy() == PricingStrategyType.FLAT ? FLAT : SLAB;
    }

    // ---- the documented worked examples (architecture §6) -------------------

    @ParameterizedTest(name = "{0} stay {1} min -> {2}")
    @CsvSource({
            "CAR,        10,    0.00",   // inside the 30 min grace
            "CAR,        45,   40.00",   // billable 15  -> 1 block
            "CAR,       180,   80.00",   // billable 150 -> 2 blocks
            "CAR,       300,  120.00",   // billable 270 -> 3 blocks
            "CAR,      1500,  200.00",   // billable 1470 -> 13 blocks = 520 -> capped
            "MOTORCYCLE, 20,   10.00",   // above the 15 min grace -> 1 block
            "BUS,        90,  120.00"    // FLAT: 80 + 1 step of 40
    })
    @DisplayName("architecture section 6 worked examples reproduce exactly")
    void workedExamples(String vehicleType, long minutes, String expected) {
        FeeRate rate = "BUS".equals(vehicleType) ? BUS
                : "CAR".equals(vehicleType) ? CAR : MOTORCYCLE;

        BigDecimal actual = strategyFor(rate).calculate(stay(rate, minutes));

        assertThat(actual).isEqualByComparingTo(new BigDecimal(expected));
        assertThat(actual.scale()).isEqualTo(2);
    }

    // ---- grace boundary (rule R3) -------------------------------------------

    @ParameterizedTest(name = "CAR {0} min -> {1}")
    @CsvSource({"0, 0.00", "1, 0.00", "29, 0.00", "30, 0.00", "31, 40.00", "32, 40.00"})
    void carGraceComparesTheTotalStay(long minutes, String expected) {
        assertThat(SLAB.calculate(stay(CAR, minutes))).isEqualByComparingTo(new BigDecimal(expected));
    }

    @ParameterizedTest(name = "MOTORCYCLE {0} min -> {1}")
    @CsvSource({"14, 0.00", "15, 0.00", "16, 10.00"})
    void motorcycleGraceBoundary(long minutes, String expected) {
        assertThat(SLAB.calculate(stay(MOTORCYCLE, minutes))).isEqualByComparingTo(new BigDecimal(expected));
    }

    // ---- block boundaries: partial blocks are charged in full ---------------

    @ParameterizedTest(name = "CAR {0} min -> {1}")
    @CsvSource({"150, 40.00", "151, 80.00", "270, 80.00", "271, 120.00"})
    void slabChargesCeilDivOfBillableOverTheBaseBlock(long minutes, String expected) {
        assertThat(SLAB.calculate(stay(CAR, minutes))).isEqualByComparingTo(new BigDecimal(expected));
    }

    @ParameterizedTest(name = "BUS {0} min -> {1}")
    @CsvSource({"59, 80.00", "60, 80.00", "61, 120.00", "120, 120.00", "121, 160.00"})
    void flatAddsOneStepPerPartialBlock(long minutes, String expected) {
        assertThat(FLAT.calculate(stay(BUS, minutes))).isEqualByComparingTo(new BigDecimal(expected));
    }

    // ---- sub-minute and degenerate stays ------------------------------------

    @Test
    @DisplayName("a stay shorter than a minute floors to 0 minutes (rule R1)")
    void subMinuteStayIsFree() {
        LocalDateTime entry = LocalDateTime.of(2026, 1, 1, 0, 0, 0);
        FeeContext ctx = FeeContext.builder()
                .rate(CAR).entryTime(entry).exitTime(entry.plusSeconds(59)).build();

        assertThat(ctx.getStayMinutes()).isZero();
        assertThat(SLAB.calculate(ctx)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("a reversed interval never produces a negative fee (rule R5)")
    void reversedIntervalIsZero() {
        LocalDateTime entry = LocalDateTime.of(2026, 1, 1, 12, 0, 0);
        FeeContext ctx = FeeContext.builder()
                .rate(CAR).entryTime(entry).exitTime(entry.minusMinutes(30)).build();

        assertThat(ctx.getStayMinutes()).isZero();
        assertThat(SLAB.calculate(ctx)).isEqualByComparingTo(BigDecimal.ZERO);
    }

    // ---- cap (resolution R4-1: session ceiling) -----------------------------

    @Test
    @DisplayName("cap_per_day caps the computed charge")
    void capApplies() {
        // 25h stay -> 13 SLAB blocks = 520, capped at 200
        assertThat(SLAB.calculate(stay(CAR, 1500))).isEqualByComparingTo(new BigDecimal("200.00"));
        // below the cap nothing is clamped
        assertThat(SLAB.calculate(stay(CAR, 300))).isEqualByComparingTo(new BigDecimal("120.00"));
    }

    @Test
    @DisplayName("a null cap means uncapped")
    void nullCapMeansUncapped() {
        FeeRate uncapped = new FeeRate(VehicleType.CAR, PricingStrategyType.SLAB, 0, 60,
                new BigDecimal("10.00"), null, null, null, LocalDate.of(2026, 1, 1));

        assertThat(SLAB.calculate(stay(uncapped, 600))).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    // ---- rounding (rule R2) -------------------------------------------------

    @Test
    @DisplayName("final amount is HALF_UP to exactly 2 decimals, never a double artifact")
    void roundingIsHalfUpAtTwoDecimals() {
        FeeRate odd = new FeeRate(VehicleType.CAR, PricingStrategyType.SLAB, 0, 60,
                new BigDecimal("1.005"), null, null, null, LocalDate.of(2026, 1, 1));

        BigDecimal actual = SLAB.calculate(stay(odd, 30));

        assertThat(actual).isEqualTo(new BigDecimal("1.01"));
        assertThat(actual.scale()).isEqualTo(2);
    }

    // ---- misconfigured rates fail loudly, never silently --------------------

    @Test
    @DisplayName("FLAT without step columns throws instead of under-charging")
    void flatRequiresStepConfiguration() {
        FeeRate broken = new FeeRate(VehicleType.BUS, PricingStrategyType.FLAT, 0, 60,
                new BigDecimal("80.00"), null, null, null, LocalDate.of(2026, 1, 1));

        assertThatThrownBy(() -> FLAT.calculate(stay(broken, 90)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("step_amount");
    }

    @Test
    @DisplayName("a zero billing block would divide by zero - rejected explicitly")
    void zeroBillingBlockIsRejected() {
        FeeRate broken = new FeeRate(VehicleType.CAR, PricingStrategyType.SLAB, 0, 0,
                new BigDecimal("10.00"), null, null, null, LocalDate.of(2026, 1, 1));

        assertThatThrownBy(() -> SLAB.calculate(stay(broken, 60)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("billing block");
    }

    // ---- FeeContext builder (architecture P6) -------------------------------

    @Test
    void feeContextRequiresARateSnapshot() {
        assertThatThrownBy(() -> FeeContext.builder()
                .entryTime(LocalDateTime.now())
                .exitTime(LocalDateTime.now())
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rate snapshot");
    }
}
