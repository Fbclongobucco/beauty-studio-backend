package com.longobuccodev.beautystudio.booking.infra.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "service")
public class ServiceEntity {

    @Id
    private String id;
    @Column(nullable = false)
    private String name;
    @Column(name = "category_id", nullable = false)
    private String categoryId;
    private String subcategory;
    @Column(name = "duration_min", nullable = false)
    private int durationMin;
    @Column(name = "duration_estimated", nullable = false)
    private boolean durationEstimated;
    @Column(name = "price_cents")
    private Integer priceCents;
    @Column(name = "professional_id", nullable = false)
    private String professionalId;
    @Column(nullable = false)
    private boolean active;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
    @Column(name = "photo_key")
    private String photoKey;
    @Column(name = "photo_content_type")
    private String photoContentType;

    protected ServiceEntity() {
    }

    public ServiceEntity(String id, String name, String categoryId, String subcategory, int durationMin,
                         boolean durationEstimated, Integer priceCents, String professionalId, boolean active,
                         int sortOrder, String photoKey, String photoContentType) {
        this.id = id;
        this.name = name;
        this.categoryId = categoryId;
        this.subcategory = subcategory;
        this.durationMin = durationMin;
        this.durationEstimated = durationEstimated;
        this.priceCents = priceCents;
        this.professionalId = professionalId;
        this.active = active;
        this.sortOrder = sortOrder;
        this.photoKey = photoKey;
        this.photoContentType = photoContentType;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getCategoryId() { return categoryId; }
    public String getSubcategory() { return subcategory; }
    public int getDurationMin() { return durationMin; }
    public boolean isDurationEstimated() { return durationEstimated; }
    public Integer getPriceCents() { return priceCents; }
    public String getProfessionalId() { return professionalId; }
    public boolean isActive() { return active; }
    public int getSortOrder() { return sortOrder; }
    public String getPhotoKey() { return photoKey; }
    public String getPhotoContentType() { return photoContentType; }
}
