package com.longobuccodev.beautystudio.booking.infra.repositories;

import com.longobuccodev.beautystudio.booking.infra.entities.ServiceEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ServiceJpaRepository extends JpaRepository<ServiceEntity, String> {

    List<ServiceEntity> findAllByOrderBySortOrderAsc();

    @Query("select coalesce(max(s.sortOrder), 0) from ServiceEntity s")
    int maxSortOrder();
}
