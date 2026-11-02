package com.longobuccodev.beautystudio.booking.core.repository;

import com.longobuccodev.beautystudio.booking.core.domain.Account;
import com.longobuccodev.beautystudio.booking.core.domain.AccountRole;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountRepository {

    Optional<Account> findById(UUID id);

    Optional<Account> findByUsername(String normalizedUsername);

    Optional<Account> findByRole(AccountRole role);

    Optional<Account> findByProfessionalId(String professionalId);

    List<Account> findAll();

    boolean existsAny();

    Account save(Account account);

    void delete(Account account);
}
