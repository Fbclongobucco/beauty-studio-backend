package com.longobuccodev.beautystudio.booking.core.exception;

import java.util.Map;

/** Dados inválidos, com a mensagem de cada campo (400 VALIDATION_ERROR). */
public class DomainValidationException extends CoreDomainException {

    private final Map<String, String> fields;

    public DomainValidationException(Map<String, String> fields) {
        super("VALIDATION_ERROR", "Confira os campos destacados.");
        this.fields = Map.copyOf(fields);
    }

    public static DomainValidationException field(String name, String message) {
        return new DomainValidationException(Map.of(name, message));
    }

    public Map<String, String> fields() {
        return fields;
    }
}
