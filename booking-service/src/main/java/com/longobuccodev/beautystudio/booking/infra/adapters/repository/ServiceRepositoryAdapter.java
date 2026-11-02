package com.longobuccodev.beautystudio.booking.infra.adapters.repository;

import com.longobuccodev.beautystudio.booking.core.domain.SalonService;
import com.longobuccodev.beautystudio.booking.core.repository.ServiceRepository;
import com.longobuccodev.beautystudio.booking.infra.adapters.mapper.EntityMapper;
import com.longobuccodev.beautystudio.booking.infra.repositories.ServiceJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ServiceRepositoryAdapter implements ServiceRepository {

    private final ServiceJpaRepository jpa;

    public ServiceRepositoryAdapter(ServiceJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<SalonService> findAll() {
        return jpa.findAllByOrderBySortOrderAsc().stream().map(EntityMapper::toDomain).toList();
    }

    @Override
    public Optional<SalonService> findById(String id) {
        return jpa.findById(id).map(EntityMapper::toDomain);
    }

    @Override
    public boolean existsById(String id) {
        return jpa.existsById(id);
    }

    @Override
    public int maxSortOrder() {
        return jpa.maxSortOrder();
    }

    @Override
    public SalonService save(SalonService service) {
        return EntityMapper.toDomain(jpa.save(EntityMapper.toEntity(service)));
    }
}
