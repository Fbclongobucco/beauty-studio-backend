package com.longobuccodev.beautystudio.messaging.application;

/** Falha que nao se resolve com nova tentativa: a mensagem vai para a DLQ. */
public class PermanentHandlingException extends RuntimeException {

    public PermanentHandlingException(String message, Throwable cause) {
        super(message, cause);
    }
}
