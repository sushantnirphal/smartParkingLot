package com.parking.lot.model;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;

/**
 * A level of the parking lot. Availability is always derived from spot rows
 * (A7 / N5) so this entity deliberately carries no counter columns.
 */
@Entity
@Table(name = "parking_floor", uniqueConstraints = @UniqueConstraint(name = "uk_floor_no", columnNames = "floor_no"))
public class ParkingFloor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "floor_no", nullable = false)
    private Integer floorNo;

    protected ParkingFloor() {
        // JPA
    }

    public ParkingFloor(Integer floorNo) {
        this.floorNo = floorNo;
    }

    public Integer getId() { return id; }
    public Integer getFloorNo() { return floorNo; }
}
