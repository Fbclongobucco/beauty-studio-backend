package com.longobuccodev.beautystudio.messaging.core;

import java.util.Arrays;
import java.util.Optional;

/** Tipos de evento de agendamento. O valor de {@link #routingKey()} e o campo {@code type} do evento. */
public enum AppointmentEventType {
    CREATED("appointment.created"),
    CONFIRMED("appointment.confirmed"),
    REJECTED("appointment.rejected"),
    CANCELLED("appointment.cancelled");

    private final String routingKey;

    AppointmentEventType(String routingKey) {
        this.routingKey = routingKey;
    }

    public String routingKey() {
        return routingKey;
    }

    public static Optional<AppointmentEventType> fromRoutingKey(String value) {
        return Arrays.stream(values()).filter(t -> t.routingKey.equals(value)).findFirst();
    }
}
