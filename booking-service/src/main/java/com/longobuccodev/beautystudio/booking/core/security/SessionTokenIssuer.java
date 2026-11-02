package com.longobuccodev.beautystudio.booking.core.security;

import com.longobuccodev.beautystudio.booking.core.domain.Account;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

/** Credencial assinada da sessão do painel (cookie {@code admin_session}). */
public interface SessionTokenIssuer {

    Duration SESSION_TTL = Duration.ofHours(12);

    String issue(Account account);

    /** Conta e versão da senha da credencial válida e não expirada; vazio caso contrário. */
    Optional<SessionClaims> verify(String token);

    record SessionClaims(UUID accountId, int passwordVersion) {
    }
}
