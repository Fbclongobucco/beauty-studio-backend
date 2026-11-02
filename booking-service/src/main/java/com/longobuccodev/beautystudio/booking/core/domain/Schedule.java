package com.longobuccodev.beautystudio.booking.core.domain;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Expediente do salão (spec appointment-booking do front): segunda a sábado, 09:00 às 20:00 no fuso
 * America/Sao_Paulo, grade de 30 minutos, serviço termina até 20:00, janela de hoje até 30 dias corridos.
 * Todas as contas de data e hora usam o fuso do salão, independente do fuso do servidor.
 */
public final class Schedule {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    public static final int OPEN_MIN = 9 * 60;
    public static final int CLOSE_MIN = 20 * 60;
    public static final int STEP_MIN = 30;
    public static final int WINDOW_DAYS = 30;
    public static final int MIN_DURATION = 30;
    public static final int MAX_DURATION = 8 * 60;

    private static final Pattern DATE_RE = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");
    private static final Pattern TIME_RE = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final DateTimeFormatter STRICT_DATE = DateTimeFormatter.ofPattern("uuuu-MM-dd")
            .withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");
    /** ISO 8601 com segundos e deslocamento, como a API de teste: 2026-11-06T11:00:00-03:00. */
    private static final DateTimeFormatter ISO_SECONDS = DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ssXXX");

    private Schedule() {
    }

    /** Data AAAA-MM-DD válida no calendário (rejeita 2026-02-30). */
    public static Optional<LocalDate> parseDate(Object value) {
        if (!(value instanceof String s) || !DATE_RE.matcher(s).matches()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(s, STRICT_DATE));
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
    }

    public static Optional<LocalTime> parseTime(Object value) {
        if (!(value instanceof String s) || !TIME_RE.matcher(s).matches()) {
            return Optional.empty();
        }
        return Optional.of(LocalTime.parse(s, HH_MM));
    }

    public static String formatTime(LocalTime time) {
        return time.format(HH_MM);
    }

    public static String formatIso(OffsetDateTime dateTime) {
        return dateTime.format(ISO_SECONDS);
    }

    public static int toMinutes(LocalTime time) {
        return time.getHour() * 60 + time.getMinute();
    }

    public static LocalTime fromMinutes(int minutes) {
        return LocalTime.of(minutes / 60, minutes % 60);
    }

    public static boolean isWorkDay(LocalDate date) {
        return date.getDayOfWeek() != DayOfWeek.SUNDAY;
    }

    /** Data e minuto do dia atuais no fuso do salão. */
    public static SalonNow now(Clock clock) {
        LocalDateTime local = LocalDateTime.ofInstant(clock.instant(), ZONE);
        return new SalonNow(local.toLocalDate(), local.getHour() * 60 + local.getMinute());
    }

    public record SalonNow(LocalDate date, int minutes) {
    }

    public static LocalDate windowEnd(LocalDate today) {
        return today.plusDays(WINDOW_DAYS);
    }

    public static boolean isInWindow(LocalDate date, LocalDate today) {
        return !date.isBefore(today) && !date.isAfter(windowEnd(today));
    }

    /** Inícios possíveis na grade para a duração, terminando até 20:00. */
    public static List<Integer> gridStarts(int durationMin) {
        List<Integer> out = new ArrayList<>();
        for (int t = OPEN_MIN; t + durationMin <= CLOSE_MIN; t += STEP_MIN) {
            out.add(t);
        }
        return out;
    }

    /** Instante da data e minuto do dia no fuso do salão. */
    public static OffsetDateTime at(LocalDate date, int minutes) {
        return date.atTime(fromMinutes(minutes)).atZone(ZONE).toOffsetDateTime();
    }

    public static boolean overlaps(int aStart, int aEnd, int bStart, int bEnd) {
        return aStart < bEnd && bStart < aEnd;
    }
}
