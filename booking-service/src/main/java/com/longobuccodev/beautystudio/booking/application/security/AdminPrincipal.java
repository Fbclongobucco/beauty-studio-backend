package com.longobuccodev.beautystudio.booking.application.security;

import com.longobuccodev.beautystudio.booking.core.domain.AccountRole;

import java.util.UUID;

/** Conta logada no painel. Master: {@code professionalId} nulo. */
public record AdminPrincipal(UUID accountId, String username, AccountRole role, String professionalId) {

    public boolean isMaster() {
        return role == AccountRole.MASTER;
    }
}
