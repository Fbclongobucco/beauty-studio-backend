package com.longobuccodev.beautystudio.notification.core.exception;

/** Erro que não se resolve com nova tentativa (ex.: número sem WhatsApp). */
public class PermanentSendException extends RuntimeException {

    public PermanentSendException(String message) {
        super(message);
    }
}
