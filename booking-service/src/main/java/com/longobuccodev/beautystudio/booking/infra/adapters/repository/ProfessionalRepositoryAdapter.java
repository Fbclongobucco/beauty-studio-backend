package com.longobuccodev.beautystudio.booking.infra.adapters.repository;

import com.longobuccodev.beautystudio.booking.core.domain.Professional;
import com.longobuccodev.beautystudio.booking.core.repository.ProfessionalRepository;
import com.longobuccodev.beautystudio.booking.infra.adapters.mapper.EntityMapper;
import com.longobuccodev.beautystudio.booking.infra.entities.ProfessionalEntity;
import com.longobuccodev.beautystudio.booking.infra.repositories.ProfessionalJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

@Component
public class ProfessionalRepositoryAdapter implements ProfessionalRepository {

    private final ProfessionalJpaRepository jpa;
    private final Clock clock;

    public ProfessionalRepositoryAdapter(ProfessionalJpaRepository jpa, Clock clock) {
        this.jpa = jpa;
        this.clock = clock;
    }

    @Override
    public List<Professional> findAll() {
        return jpa.findAllByOrderByCreatedAtAscIdAsc().stream().map(EntityMapper::toDomain).toList();
    }

    @Override
    public Optional<Professional> findById(String id) {
        return jpa.findById(id).map(EntityMapper::toDomain);
    }

    @Override
    public boolean existsById(String id) {
        return jpa.existsById(id);
    }

    @Override
    public Professional save(Professional p) {
        ProfessionalEntity entity = jpa.findById(p.id())
                .orElseGet(() -> new ProfessionalEntity(p.id(), p.name(), p.active(), p.whatsappPhone(),
                        clock.instant()));
        entity.setName(p.name());
        entity.setActive(p.active());
        entity.setWhatsappPhone(p.whatsappPhone());
        return EntityMapper.toDomain(jpa.save(entity));
    }
}
