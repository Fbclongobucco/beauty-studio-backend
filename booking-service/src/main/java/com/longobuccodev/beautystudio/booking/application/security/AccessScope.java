package com.longobuccodev.beautystudio.booking.application.security;

import com.longobuccodev.beautystudio.booking.application.exception.ForbiddenException;

/**
 * Escopo por perfil (spec admin-auth): a profissional só vê e age sobre o próprio trabalho, e o master sobre tudo.
 * A segurança é aplicada aqui, não na interface.
 */
public final class AccessScope {

    public static final String FORBIDDEN_MESSAGE = "Você não tem permissão para esta ação.";

    private AccessScope() {
    }

    /** Profissional: sempre o próprio id (filtro pedido é ignorado). Master: o filtro pedido, ou nulo. */
    public static String professionalFilter(AdminPrincipal principal, String requested) {
        if (!principal.isMaster()) {
            return principal.professionalId();
        }
        return requested == null || requested.isBlank() ? null : requested;
    }

    /** true se a sessão pode agir sobre recurso da profissional {@code ownerId}. */
    public static boolean canAccess(AdminPrincipal principal, String ownerId) {
        return principal.isMaster() || principal.professionalId().equals(ownerId);
    }

    public static void requireMaster(AdminPrincipal principal) {
        if (!principal.isMaster()) {
            throw new ForbiddenException(FORBIDDEN_MESSAGE);
        }
    }
}
