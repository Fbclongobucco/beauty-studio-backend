package com.longobuccodev.beautystudio.booking.infra.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "appointment_status_history")
public class AppointmentStatusHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "appointment_id", nullable = false)
    private UUID appointmentId;
    @Column(name = "from_status", nullable = false)
    private String fromStatus;
    @Column(name = "to_status", nullable = false)
    private String toStatus;
    @Column(name = "changed_at", nullable = false)
    private Instant changedAt;
    @Column(nullable = false)
    private String actor;
    private String reason;

    protected AppointmentStatusHistoryEntity() {
    }

    public AppointmentStatusHistoryEntity(UUID appointmentId, String fromStatus, String toStatus, Instant changedAt,
                                          String actor, String reason) {
        this.appointmentId = appointmentId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedAt = changedAt;
        this.actor = actor;
        this.reason = reason;
    }

    public Long getId() { return id; }
    public UUID getAppointmentId() { return appointmentId; }
    public String getFromStatus() { return fromStatus; }
    public String getToStatus() { return toStatus; }
    public Instant getChangedAt() { return changedAt; }
    public String getActor() { return actor; }
    public String getReason() { return reason; }
}
