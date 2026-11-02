package com.longobuccodev.beautystudio.messaging.application;

/** O broker nao confirmou a publicacao (fora do ar, timeout, nack ou mensagem devolvida). */
public class PublishFailedException extends RuntimeException {

    public PublishFailedException(String message, Throwable cause) {
        super(message, cause);
    }
}
