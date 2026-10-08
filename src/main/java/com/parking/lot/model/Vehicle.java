package com.parking.lot.model;

import java.time.LocalDateTime;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.PrePersist;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 * A vehicle known to the lot. The plate is the natural key and is always stored
 * normalised (trimmed + upper case) - see {@code EntryServiceImpl#normalize}.
 */
@Entity
@Table(name = "vehicle", uniqueConstraints = @UniqueConstraint(name = "uk_vehicle_plate", columnNames = "plate_no"))
public class Vehicle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "plate_no", nullable = false, length = 20)
    private String plateNo;

    @Enumerated(EnumType.STRING)
    @Column(name = "vehicle_type", nullable = false, length = 12)
    private VehicleType vehicleType;

    @Column(name = "make_model", length = 60)
    private String makeModel;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Vehicle() {
        // JPA
    }

    public Vehicle(String plateNo, VehicleType vehicleType) {
        this.plateNo = plateNo;
        this.vehicleType = vehicleType;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            // Stored in UTC with whole-second resolution, matching every other timestamp column.
            createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC)
                    .truncatedTo(java.time.temporal.ChronoUnit.SECONDS);
        }
    }

    public Long getId() { return id; }
    public String getPlateNo() { return plateNo; }
    public VehicleType getVehicleType() { return vehicleType; }
    public String getMakeModel() { return makeModel; }
    public void setMakeModel(String makeModel) { this.makeModel = makeModel; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
