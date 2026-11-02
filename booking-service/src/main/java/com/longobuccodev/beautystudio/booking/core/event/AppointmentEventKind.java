package com.longobuccodev.beautystudio.booking.core.event;

import com.longobuccodev.beautystudio.booking.core.domain.AppointmentStatus;

/** Evento de domínio gerado em cada criação e mudança de status de agendamento. */
public enum AppointmentEventKind {
    CREATED, CONFIRMED, REJECTED, CANCELLED;

    public static AppointmentEventKind of(AppointmentStatus status) {
        return switch (status) {
            case PENDING -> CREATED;
            case CONFIRMED -> CONFIRMED;
            case REJECTED -> REJECTED;
            case CANCELLED -> CANCELLED;
        };
    }
}
