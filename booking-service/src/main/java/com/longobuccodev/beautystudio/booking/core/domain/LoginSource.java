package com.longobuccodev.beautystudio.booking.core.domain;

/** Origem do login: ambiente (ADMIN_PASSWORD / ADMIN_USERS, protegido) ou criado pelo master no painel. */
public enum LoginSource {
    ENV("env"), DB("db");

    private final String value;

    LoginSource(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
