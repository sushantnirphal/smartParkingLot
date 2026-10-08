package com.parking.lot.testsupport;

import com.parking.lot.cache.AvailabilityCache;
import com.parking.lot.repository.FeeRateRepository;
import com.parking.lot.repository.ParkingFloorRepository;
import com.parking.lot.repository.ParkingSpotRepository;
import com.parking.lot.repository.ParkingTicketRepository;
import com.parking.lot.repository.VehicleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Base for integration tests: boots the application once (context is cached across subclasses)
 * and re-seeds the lot before every test method.
 *
 * <p>Tests deliberately run <b>without</b> a test-level {@code @Transactional} so every service
 * call opens and commits its own transaction - that is the only way the lock manager, the
 * after-commit events and {@code SELECT ... FOR UPDATE} behave exactly as they do in production.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    @Autowired protected VehicleRepository vehicleRepository;
    @Autowired protected ParkingFloorRepository floorRepository;
    @Autowired protected ParkingSpotRepository spotRepository;
    @Autowired protected ParkingTicketRepository ticketRepository;
    @Autowired protected FeeRateRepository feeRateRepository;
    @Autowired protected AvailabilityCache availabilityCache;

    protected TestLotSeeder seeder;

    @BeforeEach
    void resetLot() {
        seeder = new TestLotSeeder(floorRepository, spotRepository, ticketRepository,
                vehicleRepository, availabilityCache);
        seeder.resetDefaultLot();
    }
}
