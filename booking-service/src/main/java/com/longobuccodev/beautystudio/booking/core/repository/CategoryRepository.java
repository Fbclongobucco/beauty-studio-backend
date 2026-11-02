package com.longobuccodev.beautystudio.booking.core.repository;

import com.longobuccodev.beautystudio.booking.core.domain.Category;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository {

    /** Ordenadas por {@code sortOrder}. */
    List<Category> findAll();

    Optional<Category> findById(String id);

    boolean existsById(String id);

    int maxSortOrder();

    Category save(Category category);
}
