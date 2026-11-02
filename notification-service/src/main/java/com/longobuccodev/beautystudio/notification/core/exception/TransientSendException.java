package com.longobuccodev.beautystudio.notification.core.exception;

/** Provedor fora do ar, tempo esgotado ou circuito aberto: vale tentar de novo. */
public class TransientSendException extends RuntimeException {

    public TransientSendException(String message, Throwable cause) {
        super(message, cause);
    }
}
