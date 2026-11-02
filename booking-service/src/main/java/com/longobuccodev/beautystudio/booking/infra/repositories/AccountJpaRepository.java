package com.longobuccodev.beautystudio.booking.infra.repositories;

import com.longobuccodev.beautystudio.booking.infra.entities.AccountEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountJpaRepository extends JpaRepository<AccountEntity, UUID> {

    Optional<AccountEntity> findByUsername(String username);

    Optional<AccountEntity> findFirstByRole(String role);

    Optional<AccountEntity> findByProfessionalId(String professionalId);
}
