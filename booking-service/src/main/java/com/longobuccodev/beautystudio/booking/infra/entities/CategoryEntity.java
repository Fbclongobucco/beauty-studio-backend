package com.longobuccodev.beautystudio.booking.infra.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "category")
public class CategoryEntity {

    @Id
    private String id;
    @Column(nullable = false)
    private String name;
    private String note;
    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    protected CategoryEntity() {
    }

    public CategoryEntity(String id, String name, String note, int sortOrder) {
        this.id = id;
        this.name = name;
        this.note = note;
        this.sortOrder = sortOrder;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getNote() { return note; }
    public int getSortOrder() { return sortOrder; }
}
