package com.longobuccodev.beautystudio.booking.core.domain;

/** Conflito de pedido pendente: sobrepõe um confirmado (não pode confirmar) ou só outros pendentes (aviso). */
public enum ConflictKind {
    CONFIRMED("confirmed"), PENDING("pending");

    private final String value;

    ConflictKind(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
