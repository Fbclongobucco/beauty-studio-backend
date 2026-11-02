package com.longobuccodev.beautystudio.booking.core.domain;

import java.text.Collator;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Ganhos pelo preço gravado em cada agendamento confirmado do período (spec admin-earnings do front).
 * Pendentes, recusados e cancelados ficam fora; sem preço é contado à parte. Realizado: já terminou no instante
 * {@code now}; previsto: ainda não terminou.
 */
public final class Earnings {

    public static final int MAX_DAYS = 366;

    public record Bucket(long cents, int count) {
        Bucket add(long value) {
            return new Bucket(cents + value, count + 1);
        }
    }

    public record ByService(String name, int count, long cents) {
    }

    public record ByDay(LocalDate date, long realizedCents, long expectedCents) {
    }

    public record ByProfessional(String professionalId, String name, int count, long cents) {
    }

    public record Report(LocalDate from, LocalDate to, Bucket total, Bucket realized, Bucket expected, int unpriced,
                         List<ByService> byService, List<ByDay> byDay, List<ByProfessional> byProfessional) {
    }

    private Earnings() {
    }

    /** Erros por campo, ou vazio se o período é válido. */
    public static Optional<Map<String, String>> validatePeriod(Object from, Object to) {
        Map<String, String> fields = new LinkedHashMap<>();
        Optional<LocalDate> f = Schedule.parseDate(from);
        Optional<LocalDate> t = Schedule.parseDate(to);
        if (f.isEmpty()) {
            fields.put("from", "Data inválida (use AAAA-MM-DD).");
        }
        if (t.isEmpty()) {
            fields.put("to", "Data inválida (use AAAA-MM-DD).");
        }
        if (!fields.isEmpty()) {
            return Optional.of(fields);
        }
        if (t.get().isBefore(f.get())) {
            return Optional.of(Map.of("to", "A data final deve ser depois da inicial"));
        }
        if (f.get().plusDays(MAX_DAYS - 1).isBefore(t.get())) {
            return Optional.of(Map.of("to", "O período pode ter no máximo " + MAX_DAYS + " dias."));
        }
        return Optional.empty();
    }

    public static Report compute(List<Appointment> appointments, LocalDate from, LocalDate to, Instant now) {
        Bucket total = new Bucket(0, 0);
        Bucket realized = new Bucket(0, 0);
        Bucket expected = new Bucket(0, 0);
        int unpriced = 0;
        Map<String, long[]> services = new LinkedHashMap<>();
        Map<LocalDate, long[]> days = new TreeMap<>();
        Map<String, Object[]> profs = new LinkedHashMap<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            if (Schedule.isWorkDay(d)) {
                days.put(d, new long[2]);
            }
        }

        for (Appointment a : appointments) {
            if (a.status() != AppointmentStatus.CONFIRMED || a.date().isBefore(from) || a.date().isAfter(to)) {
                continue;
            }
            if (a.priceCents() == null) {
                unpriced++;
                continue;
            }
            long cents = a.priceCents();
            boolean done = !a.end().toInstant().isAfter(now);
            total = total.add(cents);
            if (done) {
                realized = realized.add(cents);
            } else {
                expected = expected.add(cents);
            }
            long[] s = services.computeIfAbsent(a.serviceName(), k -> new long[2]);
            s[0]++;
            s[1] += cents;
            long[] day = days.computeIfAbsent(a.date(), k -> new long[2]);
            day[done ? 0 : 1] += cents;
            Object[] p = profs.computeIfAbsent(a.professionalId(), k -> new Object[]{a.professionalName(), 0L, 0L});
            p[1] = (Long) p[1] + 1;
            p[2] = (Long) p[2] + cents;
        }

        Collator collator = Collator.getInstance(Locale.of("pt", "BR"));
        List<ByService> byService = new ArrayList<>();
        services.forEach((name, v) -> byService.add(new ByService(name, (int) v[0], v[1])));
        byService.sort(Comparator.comparingLong(ByService::cents).reversed()
                .thenComparing(ByService::name, collator));

        List<ByDay> byDay = new ArrayList<>();
        days.forEach((date, v) -> byDay.add(new ByDay(date, v[0], v[1])));

        List<ByProfessional> byProfessional = new ArrayList<>();
        profs.forEach((id, v) -> byProfessional.add(
                new ByProfessional(id, (String) v[0], ((Long) v[1]).intValue(), (Long) v[2])));
        byProfessional.sort(Comparator.comparingLong(ByProfessional::cents).reversed()
                .thenComparing(ByProfessional::name, collator));

        return new Report(from, to, total, realized, expected, unpriced, byService, byDay, byProfessional);
    }
}
