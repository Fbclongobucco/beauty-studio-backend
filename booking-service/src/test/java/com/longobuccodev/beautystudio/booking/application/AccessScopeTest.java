package com.longobuccodev.beautystudio.booking.application;

import com.longobuccodev.beautystudio.booking.application.exception.ForbiddenException;
import com.longobuccodev.beautystudio.booking.application.security.AccessScope;
import com.longobuccodev.beautystudio.booking.application.security.AdminPrincipal;
import com.longobuccodev.beautystudio.booking.core.domain.AccountRole;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatNoException;

class AccessScopeTest {

    static final AdminPrincipal MASTER = new AdminPrincipal(UUID.randomUUID(), "master", AccountRole.MASTER, null);
    static final AdminPrincipal STEFANI =
            new AdminPrincipal(UUID.randomUUID(), "stefani", AccountRole.PROFESSIONAL, "stefani");

    @Test
    void profissionalSempreFiltradaPeloProprioId() {
        assertThat(AccessScope.professionalFilter(STEFANI, "lorrayne")).isEqualTo("stefani");
        assertThat(AccessScope.professionalFilter(STEFANI, null)).isEqualTo("stefani");
    }

    @Test
    void masterUsaOFiltroPedidoOuTodas() {
        assertThat(AccessScope.professionalFilter(MASTER, "lorrayne")).isEqualTo("lorrayne");
        assertThat(AccessScope.professionalFilter(MASTER, "")).isNull();
    }

    @Test
    void acessoAoRecursoDaPropriaProfissionalOuDoMaster() {
        assertThat(AccessScope.canAccess(STEFANI, "stefani")).isTrue();
        assertThat(AccessScope.canAccess(STEFANI, "lorrayne")).isFalse();
        assertThat(AccessScope.canAccess(MASTER, "lorrayne")).isTrue();
    }

    @Test
    void acaoDoMasterComSessaoDeProfissionalEProibida() {
        assertThatThrownBy(() -> AccessScope.requireMaster(STEFANI)).isInstanceOf(ForbiddenException.class)
                .extracting(e -> ((ForbiddenException) e).code()).isEqualTo("FORBIDDEN");
        assertThatNoException().isThrownBy(() -> AccessScope.requireMaster(MASTER));
    }
}
