package com.parking.lot.service;

import com.parking.lot.config.AppConfig;
import com.parking.lot.config.ParkingProperties;
import com.parking.lot.model.FeeRate;
import com.parking.lot.model.ParkingTicket;
import com.parking.lot.model.PricingStrategyType;
import com.parking.lot.strategy.FeeContext;
import com.parking.lot.strategy.FeeStrategy;
import com.parking.lot.web.dto.FeeQuoteDto;
import com.parking.lot.web.dto.FeeQuoteRequest;
import com.parking.lot.web.error.ApiException;
import com.parking.lot.web.error.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Selects the pricing strategy and builds the immutable {@link FeeContext} (architecture P6).
 *
 * <p>The strategy comes from the {@code fee_rate} row (data driven, N3) unless
 * {@code parking.pricing-strategy} forces a global override (D1).
 */
@Service
public class FeeServiceImpl implements FeeService {

    private final Map<PricingStrategyType, FeeStrategy> strategies;
    private final RateResolver rateResolver;
    private final ParkingProperties properties;
    private final java.time.Clock clock;

    public FeeServiceImpl(Map<PricingStrategyType, FeeStrategy> strategies,
                          RateResolver rateResolver,
                          ParkingProperties properties,
                          java.time.Clock clock) {
        this.strategies = strategies;
        this.rateResolver = rateResolver;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public BigDecimal calculate(ParkingTicket ticket, LocalDateTime exitTime) {
        FeeRate rate = ticket.getFeeRate();
        if (rate == null) {
            // Should be impossible: check-in validates a rate exists (rule R6).
            throw new ApiException(ErrorCode.RATE_NOT_CONFIGURED,
                    "Ticket " + ticket.getTicketNo() + " has no rate snapshot");
        }
        FeeContext context = FeeContext.builder()
                .vehicleType(ticket.getVehicle().getVehicleType())
                .entryTime(ticket.getEntryTime())
                .exitTime(exitTime)
                .rate(rate)
                .build();
        return AppConfig.resolve(strategies, rate).calculate(context);
    }

    @Override
    public BigDecimal calculate(ParkingTicket ticket) {
        return calculate(ticket, ticket.getExitTime());
    }

    @Override
    public FeeQuoteDto quote(FeeQuoteRequest request) {
        FeeRate rate = rateResolver.resolveRequired(request.getVehicleType());
        LocalDateTime now = LocalDateTime.now(clock);
        FeeContext context = FeeContext.builder()
                .vehicleType(request.getVehicleType())
                .entryTime(now)
                .exitTime(now.plusMinutes(request.getDurationMinutes()))
                .rate(rate)
                .build();
        BigDecimal amount = AppConfig.resolve(strategies, rate).calculate(context);

        return new FeeQuoteDto(
                request.getVehicleType(),
                request.getDurationMinutes(),
                amount,
                properties.getCurrency(),
                rate.rateCardLabel(),
                rate.getStrategy().name());
    }
}
