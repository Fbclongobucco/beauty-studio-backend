package com.longobuccodev.beautystudio.booking.core.domain;

import java.util.Arrays;
import java.util.Optional;

public enum AppointmentStatus {
    PENDING("pending"), CONFIRMED("confirmed"), REJECTED("rejected"), CANCELLED("cancelled");

    private final String value;

    AppointmentStatus(String value) {
        this.value = value;
    }

    /** Valor do contrato HTTP. */
    public String value() {
        return value;
    }

    public static Optional<AppointmentStatus> of(String value) {
        return Arrays.stream(values()).filter(s -> s.value.equals(value)).findFirst();
    }
}
