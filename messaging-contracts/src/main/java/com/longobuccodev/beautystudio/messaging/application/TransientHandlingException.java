package com.longobuccodev.beautystudio.messaging.application;

/** Falha temporaria (dependencia fora do ar): a mensagem volta a ser processada mais tarde. */
public class TransientHandlingException extends RuntimeException {

    public TransientHandlingException(String message, Throwable cause) {
        super(message, cause);
    }
}
