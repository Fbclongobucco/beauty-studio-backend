package com.longobuccodev.beautystudio.booking.core.domain;

import java.time.Instant;
import java.util.UUID;

/** Registro de uma mudança de status, para o histórico. {@code actor} é o usuário do painel ou "cliente". */
public record StatusChange(UUID appointmentId, AppointmentStatus from, AppointmentStatus to, Instant at, String actor,
                           String reason) {
}
