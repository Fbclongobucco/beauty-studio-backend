package com.longobuccodev.beautystudio.booking.core.domain;

import com.longobuccodev.beautystudio.booking.core.exception.InvalidTransitionException;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Portado de beauty-studio/lib/domain/status.test.ts, mais histórico e valores congelados. */
class AppointmentTest {

    static final Instant NOW = Instant.parse("2026-11-02T13:00:00Z");

    static Appointment appt(String professional, String time, AppointmentStatus status) {
        return new Appointment(UUID.randomUUID(), "manicure", professional, "Manicure", "Stéfani", 30, 2000,
                LocalDate.parse("2026-11-06"), LocalTime.parse(time), "Maria da Silva", "+5521968117976", null,
                status, NOW, null, null, null, null, null);
    }

    @Test
    void permiteApenasOCicloDefinido() {
        assertThat(StatusAction.CONFIRM.next(AppointmentStatus.PENDING)).contains(AppointmentStatus.CONFIRMED);
        assertThat(StatusAction.REJECT.next(AppointmentStatus.PENDING)).contains(AppointmentStatus.REJECTED);
        assertThat(StatusAction.CANCEL.next(AppointmentStatus.CONFIRMED)).contains(AppointmentStatus.CANCELLED);
        assertThat(StatusAction.CONFIRM.next(AppointmentStatus.CANCELLED)).isEmpty();
        assertThat(StatusAction.CANCEL.next(AppointmentStatus.PENDING)).isEmpty();
        assertThat(StatusAction.CONFIRM.next(AppointmentStatus.REJECTED)).isEmpty();
    }

    @Test
    void transicaoRegistraDataAutorMotivoEVisto() {
        Appointment a = appt("stefani", "11:00", AppointmentStatus.CONFIRMED);
        StatusChange change = a.apply(StatusAction.CANCEL, "  Imprevisto de saúde ", NOW, "lorrayne");
        assertThat(a.status()).isEqualTo(AppointmentStatus.CANCELLED);
        assertThat(a.cancelledAt()).isEqualTo(NOW);
        assertThat(a.reason()).isEqualTo("Imprevisto de saúde");
        assertThat(a.seenByAdminAt()).isEqualTo(NOW);
        assertThat(change).isEqualTo(new StatusChange(a.id(), AppointmentStatus.CONFIRMED,
                AppointmentStatus.CANCELLED, NOW, "lorrayne", "Imprevisto de saúde"));
    }

    @Test
    void motivoLimitadoA300Caracteres() {
        Appointment a = appt("stefani", "11:00", AppointmentStatus.PENDING);
        a.apply(StatusAction.REJECT, "x".repeat(400), NOW, "stefani");
        assertThat(a.reason()).hasSize(300);
    }

    @Test
    void transicaoInvalidaFalhaSemMudarOStatus() {
        Appointment a = appt("stefani", "11:00", AppointmentStatus.CANCELLED);
        assertThatThrownBy(() -> a.apply(StatusAction.CONFIRM, null, NOW, "stefani"))
                .isInstanceOf(InvalidTransitionException.class);
        assertThat(a.status()).isEqualTo(AppointmentStatus.CANCELLED);
    }

    @Test
    void doisPendentesNoMesmoHorarioAvisoDePendente() {
        Appointment a = appt("stefani", "11:00", AppointmentStatus.PENDING);
        Appointment b = appt("stefani", "11:00", AppointmentStatus.PENDING);
        assertThat(a.conflictWith(List.of(a, b))).isEqualTo(ConflictKind.PENDING);
    }

    @Test
    void aposConfirmarUmOOutroFicaBloqueado() {
        Appointment a = appt("stefani", "11:00", AppointmentStatus.CONFIRMED);
        Appointment b = appt("stefani", "11:00", AppointmentStatus.PENDING);
        assertThat(b.conflictWith(List.of(a, b))).isEqualTo(ConflictKind.CONFIRMED);
        assertThat(a.conflictWith(List.of(a, b))).isNull();
    }

    @Test
    void outraProfissionalOuHorarioAdjacenteNaoConflita() {
        Appointment a = appt("stefani", "11:00", AppointmentStatus.CONFIRMED);
        Appointment other = appt("michele", "11:00", AppointmentStatus.PENDING);
        Appointment next = appt("stefani", "11:30", AppointmentStatus.PENDING);
        assertThat(other.conflictWith(List.of(a, other))).isNull();
        assertThat(next.conflictWith(List.of(a, next))).isNull();
    }

    @Test
    void pedidoCopiaPrecoEDuracaoDoServico() {
        SalonService manicure = new SalonService("manicure", "Manicure", "unhas", "Cuidados básicos", 30, false,
                2000, "stefani", true, 1, null, null);
        Appointment a = Appointment.request(manicure, new Professional("stefani", "Stéfani Longo Bucco", true, null),
                LocalDate.parse("2026-11-06"), LocalTime.of(11, 0), "Maria", new PhoneE164("+5521968117976"), null, NOW);
        assertThat(a.status()).isEqualTo(AppointmentStatus.PENDING);
        assertThat(a.priceCents()).isEqualTo(2000);
        assertThat(a.durationMin()).isEqualTo(30);
        assertThat(Schedule.formatIso(a.end())).isEqualTo("2026-11-06T11:30:00-03:00");
    }
}
