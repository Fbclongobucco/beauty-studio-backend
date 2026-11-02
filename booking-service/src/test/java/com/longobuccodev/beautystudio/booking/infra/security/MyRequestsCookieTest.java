package com.longobuccodev.beautystudio.booking.infra.security;

import com.longobuccodev.beautystudio.booking.core.domain.Account;
import com.longobuccodev.beautystudio.booking.core.domain.AccountRole;
import com.longobuccodev.beautystudio.booking.core.domain.LoginSource;
import com.longobuccodev.beautystudio.booking.core.domain.TrackedRequests;
import com.longobuccodev.beautystudio.booking.infra.config.BookingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class MyRequestsCookieTest {

    static final String SECRET = "segredo-de-teste-com-mais-de-32-caracteres";
    static final Instant NOW = Instant.parse("2026-11-02T12:00:00Z");
    static final UUID A = UUID.randomUUID();
    static final UUID B = UUID.randomUUID();

    static BookingProperties props() {
        return new BookingProperties(new BookingProperties.Security(SECRET, true, "Lax"), null, null, null, null, null);
    }

    static MyRequestsCookie cookie(Instant at) {
        return new MyRequestsCookie(props(), Clock.fixed(at, ZoneOffset.UTC));
    }

    @Test
    void gravaEDecodificaOsIdsNaOrdem() {
        ResponseCookie c = cookie(NOW).write(new TrackedRequests(List.of(B, A)));
        assertThat(c.getName()).isEqualTo("my_requests");
        assertThat(c.getPath()).isEqualTo("/api/appointments");
        assertThat(c.isHttpOnly()).isTrue();
        assertThat(c.isSecure()).isTrue();
        assertThat(c.getSameSite()).isEqualTo("Lax");
        assertThat(c.getMaxAge()).isEqualTo(Duration.ofDays(60));
        assertThat(cookie(NOW).decode(c.getValue()).ids()).containsExactly(B, A);
    }

    @Test
    void listaVaziaApagaOCookie() {
        ResponseCookie c = cookie(NOW).write(TrackedRequests.EMPTY);
        assertThat(c.getMaxAge()).isZero();
        assertThat(c.getValue()).isEmpty();
    }

    @Test
    void assinaturaAlteradaContaComoVazio() {
        String token = cookie(NOW).write(new TrackedRequests(List.of(A))).getValue();
        String tampered = token.substring(0, token.length() - 2) + (token.endsWith("A") ? "BB" : "AA");
        assertThat(cookie(NOW).decode(tampered).isEmpty()).isTrue();
        assertThat(cookie(NOW).decode("lixo").isEmpty()).isTrue();
    }

    @Test
    void expiradoContaComoVazio() {
        String token = cookie(NOW).write(new TrackedRequests(List.of(A))).getValue();
        assertThat(cookie(NOW.plus(Duration.ofDays(61))).decode(token).isEmpty()).isTrue();
        assertThat(cookie(NOW.plus(Duration.ofDays(59))).decode(token).ids()).containsExactly(A);
    }

    @Test
    void tokenDeUmNaoValeComoOOutro() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        JwtSessionTokenIssuer sessions = new JwtSessionTokenIssuer(props(), clock);
        Account master = new Account(UUID.randomUUID(), "master", AccountRole.MASTER, null, "hash", 1, LoginSource.ENV);
        String session = sessions.issue(master);
        assertThat(sessions.verify(session)).isPresent();
        assertThat(cookie(NOW).decode(session).isEmpty()).isTrue();

        String tracker = cookie(NOW).write(new TrackedRequests(List.of(A))).getValue();
        assertThat(sessions.verify(tracker)).isEmpty();
    }
}
