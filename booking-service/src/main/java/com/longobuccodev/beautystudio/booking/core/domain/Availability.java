package com.longobuccodev.beautystudio.booking.core.domain;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Disponibilidade por profissional: um horário está livre quando [início, início + duração) não se sobrepõe a
 * nenhum agendamento CONFIRMADO da profissional responsável. Pendentes não bloqueiam.
 */
public final class Availability {

    /** Intervalo ocupado por um agendamento confirmado. */
    public record Busy(LocalDate date, LocalTime time, int durationMin) {
    }

    public record DayInfo(LocalDate date, boolean open, boolean hasSlots) {
    }

    private Availability() {
    }

    /** Horários do dia: livres e ocupados por confirmado, ambos em HH:mm e em ordem crescente. */
    public record DaySlots(List<String> free, List<String> taken) {
    }

    /**
     * Grade do dia, já sem horários passados, separada em livres e ocupados. {@code busy} deve conter só
     * confirmados da profissional. Dia fechado ou fora da janela: as duas listas vazias.
     */
    public static DaySlots daySlots(int durationMin, LocalDate date, List<Busy> busy, Clock clock) {
        Schedule.SalonNow now = Schedule.now(clock);
        if (!Schedule.isWorkDay(date) || !Schedule.isInWindow(date, now.date())) {
            return new DaySlots(List.of(), List.of());
        }
        List<int[]> sameDay = busy.stream()
                .filter(b -> b.date().equals(date))
                .map(b -> {
                    int s = Schedule.toMinutes(b.time());
                    return new int[]{s, s + b.durationMin()};
                })
                .toList();
        boolean today = date.equals(now.date());
        List<String> free = new ArrayList<>();
        List<String> taken = new ArrayList<>();
        for (int start : Schedule.gridStarts(durationMin)) {
            if (today && start <= now.minutes()) {
                continue;
            }
            int end = start + durationMin;
            String time = Schedule.formatTime(Schedule.fromMinutes(start));
            if (sameDay.stream().noneMatch(b -> Schedule.overlaps(start, end, b[0], b[1]))) {
                free.add(time);
            } else {
                taken.add(time);
            }
        }
        return new DaySlots(free, taken);
    }

    /** Horários de início livres (HH:mm). {@code busy} deve conter só confirmados da profissional. */
    public static List<String> availableSlots(int durationMin, LocalDate date, List<Busy> busy, Clock clock) {
        return daySlots(durationMin, date, busy, clock).free();
    }

    public static boolean isSlotAvailable(int durationMin, LocalDate date, LocalTime time, List<Busy> busy,
                                          Clock clock) {
        return availableSlots(durationMin, date, busy, clock).contains(Schedule.formatTime(time));
    }

    /** Resumo por dia a partir de {@code from}, por {@code days} dias, limitado à janela. */
    public static List<DayInfo> availableDays(int durationMin, LocalDate from, int days, List<Busy> busy,
                                              Clock clock) {
        LocalDate today = Schedule.now(clock).date();
        List<DayInfo> out = new ArrayList<>();
        for (int i = 0; i < days; i++) {
            LocalDate date = from.plusDays(i);
            if (!Schedule.isInWindow(date, today)) {
                continue;
            }
            boolean open = Schedule.isWorkDay(date);
            out.add(new DayInfo(date, open, open && !availableSlots(durationMin, date, busy, clock).isEmpty()));
        }
        return out;
    }
}
