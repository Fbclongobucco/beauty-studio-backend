package com.longobuccodev.beautystudio.booking.core.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Portado de beauty-studio/lib/domain/earnings.test.ts. */
class EarningsTest {

    static final LocalDate FROM = LocalDate.parse("2026-11-01");
    static final LocalDate TO = LocalDate.parse("2026-11-30");
    static final Instant MID = OffsetDateTime.parse("2026-11-15T12:00:00-03:00").toInstant();

    static Appointment appt(String service, Integer price, String date, String time, int duration,
                            AppointmentStatus status, String prof) {
        return new Appointment(UUID.randomUUID(), service, prof, service,
                prof.equals("lorrayne") ? "Lorrayne Longobuco" : "Stéfani Longo Bucco", duration, price,
                LocalDate.parse(date), LocalTime.parse(time), "Maria", "+5521968117976", null, status,
                Instant.parse("2026-10-01T00:00:00Z"), null, null, null, null, null);
    }

    static Appointment confirmed(String service, Integer price, String date) {
        return appt(service, price, date, "10:00", 60, AppointmentStatus.CONFIRMED, "stefani");
    }

    @Test
    void soConfirmadosEntramCanceladoFicaFora() {
        Earnings.Report r = Earnings.compute(List.of(
                confirmed("Manicure", 2000, "2026-11-03"),
                confirmed("Manicure", 2000, "2026-11-04"),
                appt("Pedicure", 2500, "2026-11-05", "10:00", 60, AppointmentStatus.CANCELLED, "stefani"),
                appt("Pedicure", 2500, "2026-11-05", "10:00", 60, AppointmentStatus.PENDING, "stefani")),
                FROM, TO, MID);
        assertThat(r.total()).isEqualTo(new Earnings.Bucket(4000, 2));
    }

    @Test
    void semPrecoFicaForaDaSomaEContadoAParte() {
        Earnings.Report r = Earnings.compute(List.of(confirmed("Manicure", 2000, "2026-11-03"),
                confirmed("Avaliação", null, "2026-11-03")), FROM, TO, MID);
        assertThat(r.total()).isEqualTo(new Earnings.Bucket(2000, 1));
        assertThat(r.unpriced()).isEqualTo(1);
        assertThat(r.byService()).extracting(Earnings.ByService::name).containsExactly("Manicure");
    }

    @Test
    void divideRealizadoEPrevisto() {
        Earnings.Report r = Earnings.compute(List.of(
                appt("Box braids", 19000, "2026-11-11", "09:00", 360, AppointmentStatus.CONFIRMED, "lorrayne"),
                appt("Ghana braids", 16000, "2026-11-18", "10:00", 60, AppointmentStatus.CONFIRMED, "lorrayne"),
                // Termina 12:30 no dia 15: ainda não terminou às 12:00 → previsto
                appt("Manicure", 2000, "2026-11-15", "12:00", 30, AppointmentStatus.CONFIRMED, "stefani")),
                FROM, TO, MID);
        assertThat(r.realized()).isEqualTo(new Earnings.Bucket(19000, 1));
        assertThat(r.expected()).isEqualTo(new Earnings.Bucket(18000, 2));
        assertThat(r.total()).isEqualTo(new Earnings.Bucket(37000, 3));
    }

    @Test
    void ordenaDoMaiorParaOMenorValor() {
        Earnings.Report r = Earnings.compute(List.of(
                confirmed("Manicure", 2000, "2026-11-03"),
                confirmed("Manicure", 2000, "2026-11-04"),
                confirmed("Manicure", 2000, "2026-11-05"),
                confirmed("Fibra de vidro", 13500, "2026-11-06"),
                appt("Ghana braids", 16000, "2026-11-06", "10:00", 60, AppointmentStatus.CONFIRMED, "lorrayne")),
                FROM, TO, MID);
        assertThat(r.byService()).containsExactly(
                new Earnings.ByService("Ghana braids", 1, 16000),
                new Earnings.ByService("Fibra de vidro", 1, 13500),
                new Earnings.ByService("Manicure", 3, 6000));
        assertThat(r.byProfessional()).extracting(Earnings.ByProfessional::professionalId,
                Earnings.ByProfessional::cents, Earnings.ByProfessional::count)
                .containsExactly(org.assertj.core.groups.Tuple.tuple("stefani", 19500L, 4),
                        org.assertj.core.groups.Tuple.tuple("lorrayne", 16000L, 1));
    }

    @Test
    void seriePorDiaCobreOsDiasUteis() {
        Earnings.Report r = Earnings.compute(List.of(confirmed("Manicure", 2000, "2026-11-03")),
                LocalDate.parse("2026-11-02"), LocalDate.parse("2026-11-08"), MID);
        assertThat(r.byDay()).extracting(d -> d.date().toString()).containsExactly(
                "2026-11-02", "2026-11-03", "2026-11-04", "2026-11-05", "2026-11-06", "2026-11-07");
        assertThat(r.byDay().get(1)).isEqualTo(new Earnings.ByDay(LocalDate.parse("2026-11-03"), 2000, 0));
    }

    @Test
    void validaOPeriodo() {
        assertThat(Earnings.validatePeriod("2026-11-01", "2026-11-30")).isEmpty();
        assertThat(Earnings.validatePeriod("2026-11-20", "2026-11-10"))
                .contains(Map.of("to", "A data final deve ser depois da inicial"));
        assertThat(Earnings.validatePeriod("2026-01-01", "2027-01-02"))
                .contains(Map.of("to", "O período pode ter no máximo 366 dias."));
        assertThat(Earnings.validatePeriod("2026-01-01", "2027-01-01")).isEmpty();
        assertThat(Earnings.validatePeriod(null, "x").orElseThrow()).containsKey("from");
    }
}
