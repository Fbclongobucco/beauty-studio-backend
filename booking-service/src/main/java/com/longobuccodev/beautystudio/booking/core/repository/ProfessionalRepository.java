package com.longobuccodev.beautystudio.booking.core.repository;

import com.longobuccodev.beautystudio.booking.core.domain.Professional;

import java.util.List;
import java.util.Optional;

public interface ProfessionalRepository {

    /** Na ordem de cadastro. */
    List<Professional> findAll();

    Optional<Professional> findById(String id);

    boolean existsById(String id);

    Professional save(Professional professional);
}
