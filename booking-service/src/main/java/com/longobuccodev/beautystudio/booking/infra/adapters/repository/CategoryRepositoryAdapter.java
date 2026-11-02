package com.longobuccodev.beautystudio.booking.infra.adapters.repository;

import com.longobuccodev.beautystudio.booking.core.domain.Category;
import com.longobuccodev.beautystudio.booking.core.repository.CategoryRepository;
import com.longobuccodev.beautystudio.booking.infra.adapters.mapper.EntityMapper;
import com.longobuccodev.beautystudio.booking.infra.repositories.CategoryJpaRepository;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class CategoryRepositoryAdapter implements CategoryRepository {

    private final CategoryJpaRepository jpa;

    public CategoryRepositoryAdapter(CategoryJpaRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public List<Category> findAll() {
        return jpa.findAllByOrderBySortOrderAsc().stream().map(EntityMapper::toDomain).toList();
    }

    @Override
    public Optional<Category> findById(String id) {
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
    public Category save(Category category) {
        return EntityMapper.toDomain(jpa.save(EntityMapper.toEntity(category)));
    }
}
