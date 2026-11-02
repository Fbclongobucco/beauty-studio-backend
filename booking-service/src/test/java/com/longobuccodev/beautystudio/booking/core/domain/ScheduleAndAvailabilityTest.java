package com.longobuccodev.beautystudio.booking.core.domain;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Portado de beauty-studio/lib/domain/schedule.test.ts e availability.test.ts. */
class ScheduleAndAvailabilityTest {

    /** Domingo, 01/11/2026, 08:00 em São Paulo (11:00 UTC). Relógio em UTC, como num contêiner. */
    static final Clock SUNDAY_8AM = Clock.fixed(Instant.parse("2026-11-01T11:00:00Z"), ZoneOffset.UTC);
    static final LocalDate FRI = LocalDate.parse("2026-11-06");

    static List<String> slots(int duration, String date, List<Availability.Busy> busy, Clock clock) {
        return Availability.availableSlots(duration, LocalDate.parse(date), busy, clock);
    }

    @Test
    void ultimoHorarioDependeDaDuracao() {
        assertThat(Schedule.fromMinutes(Schedule.gridStarts(30).getLast())).isEqualTo(LocalTime.of(19, 30));
        assertThat(Schedule.fromMinutes(Schedule.gridStarts(120).getLast())).isEqualTo(LocalTime.of(18, 0));
        assertThat(Schedule.fromMinutes(Schedule.gridStarts(420).getLast())).isEqualTo(LocalTime.of(13, 0));
        assertThat(Schedule.fromMinutes(Schedule.gridStarts(30).getFirst())).isEqualTo(LocalTime.of(9, 0));
    }

    @Test
    void domingoFechadoSegundaASabadoAbertos() {
        assertThat(Schedule.isWorkDay(LocalDate.parse("2026-11-01"))).isFalse();
        assertThat(Schedule.isWorkDay(LocalDate.parse("2026-11-02"))).isTrue();
        assertThat(Schedule.isWorkDay(LocalDate.parse("2026-11-07"))).isTrue();
        assertThat(slots(30, "2026-11-08", List.of(), SUNDAY_8AM)).isEmpty();
    }

    @Test
    void servicoDe7HorasVaiDas9As13() {
        List<String> s = slots(420, "2026-11-06", List.of(), SUNDAY_8AM);
        assertThat(s.getFirst()).isEqualTo("09:00");
        assertThat(s.getLast()).isEqualTo("13:00");
    }

    @Test
    void usaOHorarioDeSaoPauloNaoODaMaquina() {
        Clock utc = Clock.fixed(Instant.parse("2026-11-03T02:30:00Z"), ZoneOffset.UTC);
        assertThat(Schedule.now(utc)).isEqualTo(new Schedule.SalonNow(LocalDate.parse("2026-11-02"), 23 * 60 + 30));
    }

    @Test
    void servidorEmUtcAinda19e30EmSaoPaulo() {
        // Segunda 02/11, 22:30 UTC = 19:30 em São Paulo: o horário de 19:30 já passou (início <= agora)
        Clock utc = Clock.fixed(Instant.parse("2026-11-02T22:29:00Z"), ZoneOffset.UTC);
        assertThat(slots(30, "2026-11-02", List.of(), utc)).containsExactly("19:30");
    }

    @Test
    void hojeAs14e10PrimeiroHorario14e30() {
        Clock monday1410 = Clock.fixed(Instant.parse("2026-11-02T17:10:00Z"), ZoneOffset.UTC);
        assertThat(slots(30, "2026-11-02", List.of(), monday1410).getFirst()).isEqualTo("14:30");
    }

    @Test
    void diasPassadosEAlemDe30DiasNaoTemHorarios() {
        assertThat(slots(30, "2026-10-31", List.of(), SUNDAY_8AM)).isEmpty();
        assertThat(Schedule.windowEnd(LocalDate.parse("2026-11-01"))).isEqualTo(LocalDate.parse("2026-12-01"));
        assertThat(slots(30, "2026-12-01", List.of(), SUNDAY_8AM)).isNotEmpty();
        assertThat(slots(30, "2026-12-02", List.of(), SUNDAY_8AM)).isEmpty();
    }

    @Test
    void validaDatasEHorarios() {
        assertThat(Schedule.parseDate("2026-02-30")).isEmpty();
        assertThat(Schedule.parseDate("06/11/2026")).isEmpty();
        assertThat(Schedule.parseDate("2026-11-06")).contains(FRI);
        assertThat(Schedule.parseTime("24:00")).isEmpty();
        assertThat(Schedule.parseTime("15:00")).contains(LocalTime.of(15, 0));
        assertThat(Schedule.formatIso(Schedule.at(FRI, 11 * 60))).isEqualTo("2026-11-06T11:00:00-03:00");
    }

    @Test
    void confirmadoBloqueiaAMesmaProfissional() {
        // "Manicure + Pedicure" (1 h) confirmado com Stéfani às 10:00
        List<Availability.Busy> busy = List.of(new Availability.Busy(FRI, LocalTime.of(10, 0), 60));
        List<String> manicure = slots(30, "2026-11-06", busy, SUNDAY_8AM);
        assertThat(manicure).doesNotContain("10:00", "10:30").contains("09:30", "11:00");
        List<String> combo = slots(60, "2026-11-06", busy, SUNDAY_8AM);
        assertThat(combo).doesNotContain("09:30").contains("09:00");
        assertThat(slots(30, "2026-11-07", busy, SUNDAY_8AM)).contains("10:00");
    }

    @Test
    void diasMarcamDomingoFechadoEDiaLotado() {
        List<Availability.Busy> full = List.of(new Availability.Busy(FRI, LocalTime.of(9, 0), 660));
        List<Availability.DayInfo> days = Availability.availableDays(30, LocalDate.parse("2026-11-01"), 7, full, SUNDAY_8AM);
        assertThat(days.getFirst()).isEqualTo(new Availability.DayInfo(LocalDate.parse("2026-11-01"), false, false));
        assertThat(days).contains(new Availability.DayInfo(FRI, true, false));
        assertThat(days).contains(new Availability.DayInfo(LocalDate.parse("2026-11-05"), true, true));
    }
}
