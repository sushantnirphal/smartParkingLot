package com.parking.lot.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.FetchType;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 * One assignable space.
 *
 * <ul>
 *   <li>{@code position} is the total order from the floor entrance (architecture N2) so the
 *       {@code nearest-entry} policy is a simple {@code ORDER BY position}.</li>
 *   <li>{@code status} transitions are always written inside the use-case transaction (D2).</li>
 * </ul>
 */
@Entity
@Table(name = "parking_spot", uniqueConstraints = @UniqueConstraint(name = "uk_spot_label", columnNames = "label"))
public class ParkingSpot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "floor_id", nullable = false)
    private ParkingFloor floor;

    @Column(name = "label", nullable = false, length = 16)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "spot_type", nullable = false, length = 12)
    private VehicleType spotType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private SpotStatus status = SpotStatus.FREE;

    @Column(name = "position", nullable = false)
    private Integer position;

    protected ParkingSpot() {
        // JPA
    }

    public ParkingSpot(ParkingFloor floor, String label, VehicleType spotType, int position) {
        this.floor = floor;
        this.label = label;
        this.spotType = spotType;
        this.position = position;
        this.status = SpotStatus.FREE;
    }

    /**
     * Exact type matching (assumption A1 - deterministic allocation).
     * The optional "motorcycle may use a CAR spot" rule lives in the policies,
     * keeping this entity policy-free.
     */
    public boolean isCompatible(VehicleType vehicleType) {
        return spotType == vehicleType;
    }

    public Long getId() { return id; }
    public ParkingFloor getFloor() { return floor; }
    public String getLabel() { return label; }
    public VehicleType getSpotType() { return spotType; }
    public SpotStatus getStatus() { return status; }
    public void setStatus(SpotStatus status) { this.status = status; }
    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }
}
