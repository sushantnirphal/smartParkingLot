package com.parking.lot.testsupport;

import com.parking.lot.cache.AvailabilityCache;
import com.parking.lot.model.ParkingFloor;
import com.parking.lot.model.ParkingSpot;
import com.parking.lot.model.ParkingTicket;
import com.parking.lot.model.Vehicle;
import com.parking.lot.model.VehicleType;
import com.parking.lot.repository.ParkingFloorRepository;
import com.parking.lot.repository.ParkingSpotRepository;
import com.parking.lot.repository.ParkingTicketRepository;
import com.parking.lot.repository.VehicleRepository;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wipes and re-seeds the lot between tests.
 *
 * <p>Deliberately a plain object (not a Spring bean) so it never leaks into the application's
 * component scan: each test builds it from its own autowired repositories.
 *
 * <p>{@code fee_rate} rows are owned by Flyway {@code V2__seed_rates.sql} and are never touched.
 */
public class TestLotSeeder {

    private final ParkingFloorRepository floorRepository;
    private final ParkingSpotRepository spotRepository;
    private final ParkingTicketRepository ticketRepository;
    private final VehicleRepository vehicleRepository;
    private final AvailabilityCache cache;

    public TestLotSeeder(ParkingFloorRepository floorRepository,
                         ParkingSpotRepository spotRepository,
                         ParkingTicketRepository ticketRepository,
                         VehicleRepository vehicleRepository,
                         AvailabilityCache cache) {
        this.floorRepository = floorRepository;
        this.spotRepository = spotRepository;
        this.ticketRepository = ticketRepository;
        this.vehicleRepository = vehicleRepository;
        this.cache = cache;
    }

    /** Mirrors {@code V3__seed_lot.sql}: 3 floors x (4 MOTORCYCLE + 6 CAR + 2 BUS). */
    public void resetDefaultLot() {
        wipe();
        for (int floorNo = 1; floorNo <= 3; floorNo++) {
            ParkingFloor floor = floorRepository.save(new ParkingFloor(floorNo));
            int position = 1;
            for (int i = 1; i <= 4; i++) {
                spotRepository.save(new ParkingSpot(floor, label(floorNo, "M", i), VehicleType.MOTORCYCLE, position++));
            }
            for (int i = 1; i <= 6; i++) {
                spotRepository.save(new ParkingSpot(floor, label(floorNo, "C", i), VehicleType.CAR, position++));
            }
            for (int i = 1; i <= 2; i++) {
                spotRepository.save(new ParkingSpot(floor, label(floorNo, "B", i), VehicleType.BUS, position++));
            }
        }
        afterSeed();
    }

    /**
     * A single-purpose floor used by the concurrency and allocation tests.
     * Floor numbers below 100 never collide with the default lot.
     */
    public void resetSingleFloor(int floorNo, int motorcycles, int cars, int buses) {
        wipe();
        ParkingFloor floor = floorRepository.save(new ParkingFloor(floorNo));
        int position = 1;
        for (int i = 1; i <= motorcycles; i++) {
            spotRepository.save(new ParkingSpot(floor, label(floorNo, "M", i), VehicleType.MOTORCYCLE, position++));
        }
        for (int i = 1; i <= cars; i++) {
            spotRepository.save(new ParkingSpot(floor, label(floorNo, "C", i), VehicleType.CAR, position++));
        }
        for (int i = 1; i <= buses; i++) {
            spotRepository.save(new ParkingSpot(floor, label(floorNo, "B", i), VehicleType.BUS, position++));
        }
        afterSeed();
    }

    /** Multi-floor variant used by the lowest-floor policy test. */
    public void resetFloors(Map<Integer, Map<VehicleType, Integer>> layout) {
        wipe();
        for (Map.Entry<Integer, Map<VehicleType, Integer>> floorEntry : layout.entrySet()) {
            int floorNo = floorEntry.getKey().intValue();
            ParkingFloor floor = floorRepository.save(new ParkingFloor(floorNo));
            int position = 1;
            for (Map.Entry<VehicleType, Integer> typeEntry : floorEntry.getValue().entrySet()) {
                for (int i = 0; i < typeEntry.getValue().intValue(); i++) {
                    spotRepository.save(new ParkingSpot(
                            floor, label(floorNo, shortCode(typeEntry.getKey()), i + 1),
                            typeEntry.getKey(), position++));
                }
            }
        }
        afterSeed();
    }

    public static Map<VehicleType, Integer> layout(int motorcycle, int car, int bus) {
        Map<VehicleType, Integer> map = new LinkedHashMap<VehicleType, Integer>();
        map.put(VehicleType.MOTORCYCLE, Integer.valueOf(motorcycle));
        map.put(VehicleType.CAR, Integer.valueOf(car));
        map.put(VehicleType.BUS, Integer.valueOf(bus));
        return map;
    }

    private void wipe() {
        // Strict FK order: tickets reference vehicle + spot, spots reference floor.
        ticketRepository.deleteAll();
        ticketRepository.flush();
        vehicleRepository.deleteAll();
        vehicleRepository.flush();
        spotRepository.deleteAll();
        spotRepository.flush();
        floorRepository.deleteAll();
        floorRepository.flush();
    }

    private void afterSeed() {
        spotRepository.flush();
        if (cache != null) {
            cache.reset();
        }
    }

    private static String shortCode(VehicleType type) {
        switch (type) {
            case MOTORCYCLE: return "M";
            case BUS: return "B";
            case CAR:
            default: return "C";
        }
    }

    private static String label(int floorNo, String code, int index) {
        return "F" + floorNo + "-" + code + index;
    }

    /** Convenience for assertions. */
    public long openTicketCount() {
        return ticketRepository.findAll().stream()
                .filter(t -> t.getStatus() == com.parking.lot.model.TicketStatus.OPEN)
                .count();
    }

    public ParkingTicket anyOpenTicket() {
        return ticketRepository.findAll().stream()
                .filter(t -> t.getStatus() == com.parking.lot.model.TicketStatus.OPEN)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("no open ticket"));
    }

    public Vehicle vehicle(String plate) {
        return vehicleRepository.findByPlateNo(plate)
                .orElseThrow(() -> new IllegalStateException("no vehicle " + plate));
    }
}
