package com.longobuccodev.beautystudio.notification.core.gateway;

/**
 * Envio de mensagem de WhatsApp, independente do provedor. Trocar de provedor exige só um novo adaptador.
 * Falha temporária: {@link com.longobuccodev.beautystudio.notification.core.exception.TransientSendException};
 * erro que não se resolve com nova tentativa (número sem WhatsApp):
 * {@link com.longobuccodev.beautystudio.notification.core.exception.PermanentSendException}.
 */
public interface WhatsAppSender {

    SendResult send(String phoneE164, String text);

    /**
     * @param providerMessageId identificador do provedor (nulo quando não houver)
     * @param logOnly           true se a mensagem só foi registrada no log (envio real desligado)
     */
    record SendResult(String providerMessageId, boolean logOnly) {
    }
}
