package com.longobuccodev.beautystudio.booking.infra.repositories;

import com.longobuccodev.beautystudio.booking.infra.entities.CategoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CategoryJpaRepository extends JpaRepository<CategoryEntity, String> {

    List<CategoryEntity> findAllByOrderBySortOrderAsc();

    @Query("select coalesce(max(c.sortOrder), 0) from CategoryEntity c")
    int maxSortOrder();
}
