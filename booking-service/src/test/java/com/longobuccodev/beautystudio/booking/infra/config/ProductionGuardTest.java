package com.longobuccodev.beautystudio.booking.infra.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionGuardTest {

    private static BookingProperties props(boolean secure, String front, String media) {
        return props(secure, front, media, null);
    }

    private static BookingProperties props(boolean secure, String front, String media, String cookieDomain) {
        return new BookingProperties(
                new BookingProperties.Security("x".repeat(32), secure, "Lax", cookieDomain),
                null,
                new BookingProperties.Public(front, media),
                null, null, null);
    }

    @Test
    void aceitaHttpsECookieSecure() {
        assertThatCode(() -> new ProductionGuard(
                props(true, "https://salao.com.br", "https://api.salao.com.br", "salao.com.br")).check())
                .doesNotThrowAnyException();
    }

    @Test
    void aceitaLocalhostParaTestarOPerfilNaMaquina() {
        assertThatCode(() -> new ProductionGuard(
                props(true, "http://localhost:3000", "http://localhost:8090")).check())
                .doesNotThrowAnyException();
    }

    @Test
    void recusaCookieSemSecureEUrlHttp() {
        assertThatThrownBy(() -> new ProductionGuard(
                props(false, "http://salao.com.br", "https://api.salao.com.br")).check())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("APP_COOKIE_SECURE")
                .hasMessageContaining("APP_PUBLIC_FRONT_URL deve usar https");
    }

    @Test
    void frontEApiEmSubdominiosExigemCookieDomain() {
        assertThatThrownBy(() -> new ProductionGuard(
                props(true, "https://salao.com.br", "https://api.salao.com.br")).check())
                .hasMessageContaining("APP_COOKIE_DOMAIN deve ser definido");
        assertThatCode(() -> new ProductionGuard(
                props(true, "https://salao.com.br", "https://api.salao.com.br", "salao.com.br")).check())
                .doesNotThrowAnyException();
        assertThatCode(() -> new ProductionGuard(
                props(true, "https://www.salao.com.br", "https://api.salao.com.br", ".salao.com.br")).check())
                .doesNotThrowAnyException();
    }

    @Test
    void cookieDomainQueNaoCobreOsDoisHostsERecusado() {
        assertThatThrownBy(() -> new ProductionGuard(
                props(true, "https://salao.vercel.app", "https://api.salao.com.br", "salao.com.br")).check())
                .hasMessageContaining("deve cobrir salao.vercel.app e api.salao.com.br");
    }
}
