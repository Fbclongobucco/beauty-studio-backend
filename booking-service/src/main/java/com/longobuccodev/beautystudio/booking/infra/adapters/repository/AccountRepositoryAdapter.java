package com.longobuccodev.beautystudio.booking.infra.adapters.repository;

import com.longobuccodev.beautystudio.booking.core.domain.Account;
import com.longobuccodev.beautystudio.booking.core.domain.AccountRole;
import com.longobuccodev.beautystudio.booking.core.repository.AccountRepository;
import com.longobuccodev.beautystudio.booking.infra.adapters.mapper.EntityMapper;
import com.longobuccodev.beautystudio.booking.infra.repositories.AccountJpaRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class AccountRepositoryAdapter implements AccountRepository {

    private final AccountJpaRepository jpa;
    private final Clock clock;

    public AccountRepositoryAdapter(AccountJpaRepository jpa, Clock clock) {
        this.jpa = jpa;
        this.clock = clock;
    }

    @Override
    public Optional<Account> findById(UUID id) {
        return jpa.findById(id).map(EntityMapper::toDomain);
    }

    @Override
    public Optional<Account> findByUsername(String normalizedUsername) {
        return jpa.findByUsername(normalizedUsername).map(EntityMapper::toDomain);
    }

    @Override
    public Optional<Account> findByRole(AccountRole role) {
        return jpa.findFirstByRole(role.value()).map(EntityMapper::toDomain);
    }

    @Override
    public Optional<Account> findByProfessionalId(String professionalId) {
        return jpa.findByProfessionalId(professionalId).map(EntityMapper::toDomain);
    }

    @Override
    public List<Account> findAll() {
        return jpa.findAll().stream().map(EntityMapper::toDomain).toList();
    }

    @Override
    public boolean existsAny() {
        return jpa.count() > 0;
    }

    @Override
    public Account save(Account account) {
        return EntityMapper.toDomain(jpa.saveAndFlush(EntityMapper.toEntity(account, clock.instant())));
    }

    @Override
    public void delete(Account account) {
        jpa.deleteById(account.id());
        jpa.flush();
    }
}
