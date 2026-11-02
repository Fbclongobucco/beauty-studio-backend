package com.longobuccodev.beautystudio.booking.infra.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.cors.CorsConfiguration;

import static org.assertj.core.api.Assertions.assertThat;

/** Perfil standalone: o booking faz o CORS que no perfil completo é do gateway. */
class CorsSettingsTest {

    @Test
    void semOrigensFicaDesligado() {
        assertThat(new CorsSettings("").enabled()).isFalse();
        assertThat(new CorsSettings(" , ").enabled()).isFalse();
    }

    @Test
    void origensSeparadasPorVirgulaComCredenciais() {
        CorsSettings s = new CorsSettings("https://salao.com.br, https://www.salao.com.br");
        assertThat(s.enabled()).isTrue();
        CorsConfiguration c = s.source().getCorsConfiguration(new MockHttpServletRequest("GET", "/api/services"));
        assertThat(c.getAllowedOrigins()).containsExactly("https://salao.com.br", "https://www.salao.com.br");
        assertThat(c.getAllowCredentials()).isTrue();
        assertThat(c.checkOrigin("https://malicioso.example")).isNull();
    }
}
