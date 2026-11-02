package com.longobuccodev.beautystudio.booking.infra.security;

import com.longobuccodev.beautystudio.booking.infra.config.BookingProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseCookie;

import static org.assertj.core.api.Assertions.assertThat;

class SessionCookiesTest {

    static SessionCookies cookies(String domain) {
        return new SessionCookies(new BookingProperties(
                new BookingProperties.Security("x".repeat(32), true, "Lax", domain), null, null, null, null, null));
    }

    @Test
    void semDominioOCookieFicaSoNoHostDaApi() {
        assertThat(cookies(null).create("t").getDomain()).isNull();
        assertThat(cookies("  ").create("t").getDomain()).isNull();
    }

    @Test
    void comDominioOFrontEmOutroSubdominioTambemVeASessaoEOLogoutApagaNoMesmoDominio() {
        ResponseCookie login = cookies("salao.com.br").create("t");
        assertThat(login.getDomain()).isEqualTo("salao.com.br");
        assertThat(login.toString()).contains("Domain=salao.com.br", "HttpOnly", "Secure", "SameSite=Lax");
        assertThat(cookies("salao.com.br").clear().getDomain()).isEqualTo("salao.com.br");
    }
}
