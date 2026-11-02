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
        checkCookieDomain(properties, problems);
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Configuracao invalida para o perfil prod: " + String.join("; ", problems));
        }
    }

    /**
     * Front e API em hosts diferentes: o cookie de sessão precisa de APP_COOKIE_DOMAIN, comum aos dois hosts,
     * senão o front nunca vê a sessão e manda sempre para o login.
     */
    private static void checkCookieDomain(BookingProperties properties, List<String> problems) {
        String front = host(properties.publicUrls().frontUrl());
        String api = host(properties.publicUrls().mediaUrl());
        if (front == null || api == null || front.equals(api)) {
            return;
        }
        String domain = properties.security().cookieDomain();
        if (domain == null || domain.isBlank()) {
            problems.add("APP_COOKIE_DOMAIN deve ser definido: front (" + front + ") e API (" + api
                    + ") estão em hosts diferentes");
            return;
        }
        String d = domain.strip().replaceFirst("^\\.", "").toLowerCase();
        if (!coveredBy(front, d) || !coveredBy(api, d)) {
            problems.add("APP_COOKIE_DOMAIN (" + d + ") deve cobrir " + front + " e " + api);
        }
    }

    private static boolean coveredBy(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    private static String host(String url) {
        try {
            String h = URI.create(url == null ? "" : url.trim()).getHost();
            return h == null ? null : h.toLowerCase();
        } catch (IllegalArgumentException e) {
            return null;
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
