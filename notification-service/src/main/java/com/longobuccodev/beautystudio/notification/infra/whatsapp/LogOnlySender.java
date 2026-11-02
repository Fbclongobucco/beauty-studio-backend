package com.longobuccodev.beautystudio.notification.infra.whatsapp;

import com.longobuccodev.beautystudio.notification.core.gateway.WhatsAppSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Envio desligado ({@code NOTIFICATION_SEND_ENABLED=false}): registra o texto completo no log, sem chamar o
 * provedor. Modo explícito de desenvolvimento, por isso o log não mascara o conteúdo.
 */
public class LogOnlySender implements WhatsAppSender {

    private static final Logger log = LoggerFactory.getLogger(LogOnlySender.class);

    @Override
    public SendResult send(String phoneE164, String text) {
        log.info("[WhatsApp somente log] para {}:\n{}", phoneE164, text);
        return new SendResult(null, true);
    }
}
