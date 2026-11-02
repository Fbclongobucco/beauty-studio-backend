package com.longobuccodev.beautystudio.booking.core.event;

import com.longobuccodev.beautystudio.booking.core.domain.Appointment;
import com.longobuccodev.beautystudio.booking.core.domain.Professional;

/**
 * Grava o evento para publicação posterior. MUST ser chamado na mesma transação da mudança:
 * se a transação falhar, o evento não existe.
 */
public interface EventRecorder {

    void record(AppointmentEventKind kind, Appointment appointment, Professional professional);
}
