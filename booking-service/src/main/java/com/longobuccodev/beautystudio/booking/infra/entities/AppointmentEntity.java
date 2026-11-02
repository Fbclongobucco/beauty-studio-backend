package com.longobuccodev.beautystudio.booking.infra.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(name = "appointment")
public class AppointmentEntity {

    @Id
    private UUID id;
    @Column(name = "service_id", nullable = false)
    private String serviceId;
    @Column(name = "professional_id", nullable = false)
    private String professionalId;
    @Column(name = "service_name", nullable = false)
    private String serviceName;
    @Column(name = "professional_name", nullable = false)
    private String professionalName;
    @Column(name = "duration_min", nullable = false)
    private int durationMin;
    @Column(name = "price_cents")
    private Integer priceCents;
    @Column(nullable = false)
    private LocalDate date;
    @Column(nullable = false)
    private LocalTime time;
    @Column(name = "starts_at", nullable = false)
    private OffsetDateTime startsAt;
    @Column(name = "ends_at", nullable = false)
    private OffsetDateTime endsAt;
    @Column(name = "customer_name", nullable = false)
    private String customerName;
    @Column(name = "customer_phone", nullable = false)
    private String customerPhone;
    private String notes;
    @Column(nullable = false)
    private String status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "confirmed_at")
    private Instant confirmedAt;
    @Column(name = "rejected_at")
    private Instant rejectedAt;
    @Column(name = "cancelled_at")
    private Instant cancelledAt;
    private String reason;
    @Column(name = "seen_at")
    private Instant seenAt;

    protected AppointmentEntity() {
    }

    public AppointmentEntity(UUID id, String serviceId, String professionalId, String serviceName,
                             String professionalName, int durationMin, Integer priceCents, LocalDate date,
                             LocalTime time, OffsetDateTime startsAt, OffsetDateTime endsAt, String customerName,
                             String customerPhone, String notes, String status, Instant createdAt,
                             Instant confirmedAt, Instant rejectedAt, Instant cancelledAt, String reason,
                             Instant seenAt) {
        this.id = id;
        this.serviceId = serviceId;
        this.professionalId = professionalId;
        this.serviceName = serviceName;
        this.professionalName = professionalName;
        this.durationMin = durationMin;
        this.priceCents = priceCents;
        this.date = date;
        this.time = time;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.customerName = customerName;
        this.customerPhone = customerPhone;
        this.notes = notes;
        this.status = status;
        this.createdAt = createdAt;
        this.confirmedAt = confirmedAt;
        this.rejectedAt = rejectedAt;
        this.cancelledAt = cancelledAt;
        this.reason = reason;
        this.seenAt = seenAt;
    }

    public UUID getId() { return id; }
    public String getServiceId() { return serviceId; }
    public String getProfessionalId() { return professionalId; }
    public String getServiceName() { return serviceName; }
    public String getProfessionalName() { return professionalName; }
    public int getDurationMin() { return durationMin; }
    public Integer getPriceCents() { return priceCents; }
    public LocalDate getDate() { return date; }
    public LocalTime getTime() { return time; }
    public String getCustomerName() { return customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public String getNotes() { return notes; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getConfirmedAt() { return confirmedAt; }
    public Instant getRejectedAt() { return rejectedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public String getReason() { return reason; }
    public Instant getSeenAt() { return seenAt; }
}
