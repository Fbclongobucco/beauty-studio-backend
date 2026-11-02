package com.longobuccodev.beautystudio.messaging.application;

/**
 * Porta de consumo. Retorno normal confirma (ack) a mensagem.
 * {@link TransientHandlingException} retenta com intervalo crescente;
 * {@link PermanentHandlingException} manda direto para a DLQ.
 */
public interface EventHandler<T> {

    void handle(T event);
}
