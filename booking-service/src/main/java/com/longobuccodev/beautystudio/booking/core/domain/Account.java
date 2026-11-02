package com.longobuccodev.beautystudio.booking.core.domain;

import java.util.Locale;
import java.util.UUID;

/**
 * Conta do painel. A senha só existe como hash. {@code passwordVersion} muda a cada troca de senha e
 * invalida as sessões abertas dessa conta.
 */
public record Account(UUID id, String username, AccountRole role, String professionalId, String passwordHash,
                      int passwordVersion, LoginSource source) {

    /** Usuário comparado sem diferenciar maiúsculas e sem espaços nas pontas. */
    public static String normalizeUsername(String username) {
        return username == null ? "" : username.strip().toLowerCase(Locale.ROOT);
    }

    public Account withPassword(String newHash, LoginSource newSource) {
        return new Account(id, username, role, professionalId, newHash, passwordVersion + 1, newSource);
    }

    public Account withUsername(String newUsername) {
        return new Account(id, newUsername, role, professionalId, passwordHash, passwordVersion, source);
    }

    public Account withSource(LoginSource newSource) {
        return new Account(id, username, role, professionalId, passwordHash, passwordVersion, newSource);
    }
}
