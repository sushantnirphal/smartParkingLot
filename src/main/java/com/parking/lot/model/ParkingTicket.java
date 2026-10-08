package com.parking.lot.model;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
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
import javax.persistence.Version;

/**
 * Entry/exit record - the "parking transaction" of the brief.
 *
 * <p>The {@code feeRate} reference taken at check-in is the rate snapshot (architecture N4):
 * because rates are append-only with {@code valid_from} (N3), a later price change creates a new
 * row and can never re-price an open ticket.
 */
@Entity
@Table(name = "parking_ticket", uniqueConstraints = @UniqueConstraint(name = "uk_ticket_no", columnNames = "ticket_no"))
public class ParkingTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ticket_no", nullable = false, length = 24)
    private String ticketNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "vehicle_id", nullable = false)
    private Vehicle vehicle;

    /** Kept after close for audit (architecture N1) - hence nullable, never cleared. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "spot_id")
    private ParkingSpot spot;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "fee_rate_id")
    private FeeRate feeRate;

    @Column(name = "entry_time", nullable = false)
    private LocalDateTime entryTime;

    @Column(name = "exit_time")
    private LocalDateTime exitTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 10)
    private TicketStatus status = TicketStatus.OPEN;

    @Column(name = "amount_charged")
    private BigDecimal amountCharged;

    @Column(name = "duration_minutes")
    private Integer durationMinutes;

    /** Optimistic locking (architecture N7 / threat R6). */
    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    protected ParkingTicket() {
        // JPA
    }

    public ParkingTicket(String ticketNo, Vehicle vehicle, ParkingSpot spot, FeeRate feeRate, LocalDateTime entryTime) {
        this.ticketNo = ticketNo;
        this.vehicle = vehicle;
        this.spot = spot;
        this.feeRate = feeRate;
        this.entryTime = entryTime;
        this.status = TicketStatus.OPEN;
    }

    /** Closes the ticket and freezes the charge (architecture A5). */
    public void close(LocalDateTime at, BigDecimal fee, int durationMinutes) {
        if (status != TicketStatus.OPEN) {
            throw new IllegalStateException("Ticket " + ticketNo + " is already " + status);
        }
        this.exitTime = at;
        this.amountCharged = fee;
        this.durationMinutes = durationMinutes;
        this.status = TicketStatus.CLOSED;
    }

    /** Stay length in whole minutes, floored (rule R1). */
    public int stayMinutes(LocalDateTime at) {
        LocalDateTime end = (at != null) ? at : exitTime;
        if (end == null || end.isBefore(entryTime)) {
            return 0;
        }
        return (int) Math.max(0, Duration.between(entryTime, end).toMinutes());
    }

    public int stayMinutes() {
        return stayMinutes(exitTime);
    }

    public Long getId() { return id; }
    public String getTicketNo() { return ticketNo; }
    public Vehicle getVehicle() { return vehicle; }
    public ParkingSpot getSpot() { return spot; }
    public FeeRate getFeeRate() { return feeRate; }
    public LocalDateTime getEntryTime() { return entryTime; }
    public LocalDateTime getExitTime() { return exitTime; }
    public TicketStatus getStatus() { return status; }
    public BigDecimal getAmountCharged() { return amountCharged; }
    public Integer getDurationMinutes() { return durationMinutes; }
    public Long getVersion() { return version; }
}
