package com.longobuccodev.beautystudio.booking.infra.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "professional")
public class ProfessionalEntity {

    @Id
    private String id;
    @Column(nullable = false)
    private String name;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "whatsapp_phone")
    private String whatsappPhone;
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ProfessionalEntity() {
    }

    public ProfessionalEntity(String id, String name, boolean active, String whatsappPhone, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.active = active;
        this.whatsappPhone = whatsappPhone;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
    public String getWhatsappPhone() { return whatsappPhone; }
    public Instant getCreatedAt() { return createdAt; }
    public void setName(String name) { this.name = name; }
    public void setActive(boolean active) { this.active = active; }
    public void setWhatsappPhone(String whatsappPhone) { this.whatsappPhone = whatsappPhone; }
}
