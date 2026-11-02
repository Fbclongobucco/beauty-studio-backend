package com.longobuccodev.beautystudio.notification.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * @param sendEnabled   false: só registra a mensagem no log (desenvolvimento e testes)
 * @param salonWhatsapp WhatsApp do salão em E.164, incluído nos avisos de recusa e cancelamento
 * @param frontUrl      base do link do painel
 */
@ConfigurationProperties("beauty.notification")
public record NotificationProperties(boolean sendEnabled, String salonWhatsapp, String frontUrl, Evolution evolution) {

    public record Evolution(URI baseUrl, String apiKey, String instance, Duration timeout) {
    }
}
