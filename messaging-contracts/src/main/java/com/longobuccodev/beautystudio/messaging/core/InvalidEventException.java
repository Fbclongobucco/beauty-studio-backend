package com.longobuccodev.beautystudio.messaging.core;

/** Evento malformado: falta campo obrigatorio ou o tipo e desconhecido. Nao adianta retentar. */
public class InvalidEventException extends RuntimeException {

    public InvalidEventException(String message) {
        super(message);
    }
}
