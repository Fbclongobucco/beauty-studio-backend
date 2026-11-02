package com.longobuccodev.beautystudio.booking.infra.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * CORS do booking-service quando ele atende sem o gateway (perfil standalone). Mesmas regras do gateway:
 * origens do front, com credenciais (cookie de sessão). Sem origens configuradas, fica desligado.
 */
@Component
public class CorsSettings {

    private final List<String> origins;

    public CorsSettings(@Value("${beauty.cors.allowed-origins:}") String origins) {
        this.origins = Arrays.stream(origins.split(",")).map(String::strip).filter(s -> !s.isEmpty()).toList();
    }

    public boolean enabled() {
        return !origins.isEmpty();
    }

    public UrlBasedCorsConfigurationSource source() {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(origins);
        cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cors.setAllowedHeaders(List.of("*"));
        cors.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }
}
