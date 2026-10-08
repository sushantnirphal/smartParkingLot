package com.parking.lot;

import com.parking.lot.config.ParkingProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Smart Parking Lot System entry point.
 *
 * <p>Layering and package map are defined in {@code architecture.md} §2:
 * <pre>
 *   com.parking.lot
 *     |- web        REST controllers, DTOs, error mapping      (Controller layer)
 *     |- service    use cases, @Transactional boundaries       (Service layer)
 *     |- strategy   pure allocation / pricing algorithms        (Strategy layer)
 *     |- model      entities + enums, zero Spring imports       (Domain layer)
 *     |- repository Spring Data JPA repositories                (Persistence layer)
 *     |- lock       striped lock manager                        (Infrastructure)
 *     |- cache      availability cache                          (Infrastructure)
 *     |- event      spot change events (Observer, P4)           (Infrastructure)
 *     |- config     Clock bean, parking properties              (Infrastructure)
 * </pre>
 */
@SpringBootApplication
@EnableConfigurationProperties(ParkingProperties.class)
public class ParkingLotApplication {

    public static void main(String[] args) {
        SpringApplication.run(ParkingLotApplication.class, args);
    }
}
