package com.longobuccodev.beautystudio.booking.infra.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account")
public class AccountEntity {

    @Id
    private UUID id;
    @Column(nullable = false, unique = true)
    private String username;
    @Column(nullable = false)
    private String role;
    @Column(name = "professional_id")
    private String professionalId;
    @Column(name = "password_hash", nullable = false)
    private String passwordHash;
    @Column(name = "password_version", nullable = false)
    private int passwordVersion;
    @Column(nullable = false)
    private String source;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountEntity() {
    }

    public AccountEntity(UUID id, String username, String role, String professionalId, String passwordHash,
                         int passwordVersion, String source, Instant updatedAt) {
        this.id = id;
        this.username = username;
        this.role = role;
        this.professionalId = professionalId;
        this.passwordHash = passwordHash;
        this.passwordVersion = passwordVersion;
        this.source = source;
        this.updatedAt = updatedAt;
    }

    public UUID getId() { return id; }
    public String getUsername() { return username; }
    public String getRole() { return role; }
    public String getProfessionalId() { return professionalId; }
    public String getPasswordHash() { return passwordHash; }
    public int getPasswordVersion() { return passwordVersion; }
    public String getSource() { return source; }
}
