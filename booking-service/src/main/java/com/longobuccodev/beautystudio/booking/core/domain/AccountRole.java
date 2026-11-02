package com.longobuccodev.beautystudio.booking.core.domain;

public enum AccountRole {
    MASTER("master"), PROFESSIONAL("professional");

    private final String value;

    AccountRole(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
