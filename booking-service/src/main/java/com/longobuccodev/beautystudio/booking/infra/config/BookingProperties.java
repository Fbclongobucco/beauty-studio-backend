package com.longobuccodev.beautystudio.booking.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

import java.net.URI;
import java.time.Duration;

/** Configuração do booking-service ({@code beauty.*}), toda vinda do ambiente. */
@ConfigurationProperties("beauty")
public record BookingProperties(Security security, Admin admin, Public publicUrls, Storage storage, Login login,
                                Outbox outbox) {

    /** @param jwtSecret segredo HS256 da sessão, com pelo menos 32 caracteres */
    public record Security(String jwtSecret, boolean cookieSecure, String cookieSameSite) {
    }

    /** Contas do ambiente: master e {@code ADMIN_USERS} ({@code id:senha;id:senha}). */
    public record Admin(String username, String password, String users) {
    }

    /** @param frontUrl links do status do pedido e do painel; @param mediaUrl base pública das fotos (gateway) */
    public record Public(String frontUrl, String mediaUrl) {
    }

    public record Storage(URI endpoint, String accessKey, String secretKey, String bucket, DataSize maxPhotoSize,
                          Duration timeout) {
    }

    /**
     * @param trustedProxyHops proxies confiáveis à frente do booking: 1 = só o gateway (usa a última entrada do
     *                         X-Forwarded-For, que o cliente não consegue forjar); 2 = gateway atrás de um proxy reverso
     */
    public record Login(int maxFailures, Duration window, int trustedProxyHops) {
    }

    public record Outbox(boolean relayEnabled, Duration relayInterval, int batchSize, Duration retention) {
    }
}
