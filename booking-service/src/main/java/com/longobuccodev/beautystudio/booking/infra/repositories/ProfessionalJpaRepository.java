package com.longobuccodev.beautystudio.booking.infra.repositories;

import com.longobuccodev.beautystudio.booking.infra.entities.ProfessionalEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProfessionalJpaRepository extends JpaRepository<ProfessionalEntity, String> {

    List<ProfessionalEntity> findAllByOrderByCreatedAtAscIdAsc();
}
