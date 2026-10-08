package com.parking.lot.service;

import com.parking.lot.model.FeeRate;
import com.parking.lot.model.VehicleType;
import com.parking.lot.repository.FeeRateRepository;
import com.parking.lot.web.error.ApiException;
import com.parking.lot.web.error.ErrorCode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Resolves the active rate card (architecture N3 / rule R6).
 *
 * <p>Rate rows are append-only with {@code valid_from}, so the row we pick here and attach to the
 * ticket is a stable snapshot: a later price change inserts a new row and cannot re-price an
 * open ticket (architecture N4).
 */
@Component
public class RateResolver {

    private final FeeRateRepository feeRateRepository;
    private final Clock clock;

    public RateResolver(FeeRateRepository feeRateRepository, Clock clock) {
        this.feeRateRepository = feeRateRepository;
        this.clock = clock;
    }

    public FeeRate resolveRequired(VehicleType vehicleType) {
        return findActive(vehicleType).orElseThrow(() -> new ApiException(
                ErrorCode.RATE_NOT_CONFIGURED,
                "No active fee rate configured for " + vehicleType));
    }

    public Optional<FeeRate> findActive(VehicleType vehicleType) {
        return feeRateRepository.findTop1ByVehicleTypeAndActiveTrueAndValidFromLessThanEqualOrderByValidFromDesc(
                vehicleType, LocalDate.now(clock));
    }
}
