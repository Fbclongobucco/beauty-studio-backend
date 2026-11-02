package com.longobuccodev.beautystudio.booking.core.repository;

import com.longobuccodev.beautystudio.booking.core.domain.SalonService;

import java.util.List;
import java.util.Optional;

public interface ServiceRepository {

    /** Ordenados por {@code sortOrder}. */
    List<SalonService> findAll();

    Optional<SalonService> findById(String id);

    boolean existsById(String id);

    int maxSortOrder();

    SalonService save(SalonService service);
}
