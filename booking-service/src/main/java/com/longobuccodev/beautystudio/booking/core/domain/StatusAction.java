package com.longobuccodev.beautystudio.booking.core.domain;

import java.util.Arrays;
import java.util.Optional;

/** Ações do painel e a transição que cada uma permite. */
public enum StatusAction {
    CONFIRM("confirm", AppointmentStatus.PENDING, AppointmentStatus.CONFIRMED),
    REJECT("reject", AppointmentStatus.PENDING, AppointmentStatus.REJECTED),
    CANCEL("cancel", AppointmentStatus.CONFIRMED, AppointmentStatus.CANCELLED);

    private final String value;
    private final AppointmentStatus from;
    private final AppointmentStatus to;

    StatusAction(String value, AppointmentStatus from, AppointmentStatus to) {
        this.value = value;
        this.from = from;
        this.to = to;
    }

    public String value() {
        return value;
    }

    /** Próximo status, ou vazio se a ação não vale no status atual. */
    public Optional<AppointmentStatus> next(AppointmentStatus current) {
        return from == current ? Optional.of(to) : Optional.empty();
    }

    public static Optional<StatusAction> of(String value) {
        return Arrays.stream(values()).filter(a -> a.value.equals(value)).findFirst();
    }
}
