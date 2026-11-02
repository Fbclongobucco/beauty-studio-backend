package com.longobuccodev.beautystudio.booking.infra.config;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * No perfil {@code prod}, impede a subida com configuração de desenvolvimento: cookie de sessão sem Secure
 * ou URL pública sem HTTPS (exceto localhost, para testar o perfil na própria máquina).
 */
@Component
@Profile("prod")
class ProductionGuard {

    private final BookingProperties properties;

    ProductionGuard(BookingProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    void check() {
        List<String> problems = new ArrayList<>();
        if (!properties.security().cookieSecure()) {
            problems.add("APP_COOKIE_SECURE deve ser true");
        }
        requireHttps("APP_PUBLIC_FRONT_URL", properties.publicUrls().frontUrl(), problems);
        requireHttps("APP_PUBLIC_MEDIA_URL", properties.publicUrls().mediaUrl(), problems);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Configuracao invalida para o perfil prod: " + String.join("; ", problems));
        }
    }

    private static void requireHttps(String name, String value, List<String> problems) {
        URI uri;
        try {
            uri = URI.create(value == null ? "" : value.trim());
        } catch (IllegalArgumentException e) {
            problems.add(name + " invalida");
            return;
        }
        boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
        if (!"https".equals(uri.getScheme()) && !local) {
            problems.add(name + " deve usar https");
        }
    }
}
