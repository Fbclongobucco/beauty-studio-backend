package com.longobuccodev.beautystudio.booking.application.validation;

import com.longobuccodev.beautystudio.booking.core.domain.Duration30;
import com.longobuccodev.beautystudio.booking.core.domain.Money;
import com.longobuccodev.beautystudio.booking.core.domain.PhoneE164;
import com.longobuccodev.beautystudio.booking.core.domain.Schedule;
import com.longobuccodev.beautystudio.booking.core.exception.DomainValidationException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Validação dos corpos JSON, com as mesmas regras e mensagens da API de teste
 * (beauty-studio/lib/domain/validation.ts). Campo presente com valor nulo conta como informado.
 */
public final class InputValidators {

    private InputValidators() {
    }

    public record BookingInput(String serviceId, LocalDate date, LocalTime time, String customerName,
                               PhoneE164 customerPhone, String notes) {
    }

    /** Campos presentes no corpo; ausente = não alterar (edição parcial). */
    public record ServiceInput(Optional<String> name, Optional<String> categoryId, Optional<Optional<String>> subcategory,
                               Optional<Integer> durationMin, Optional<Optional<Integer>> priceCents,
                               Optional<String> professionalId, Optional<Boolean> active) {
    }

    public static BookingInput booking(Map<String, Object> body) {
        Map<String, Object> b = body == null ? Map.of() : body;
        Map<String, String> fields = new LinkedHashMap<>();

        Object serviceId = b.get("serviceId");
        if (!(serviceId instanceof String s) || s.isEmpty()) {
            fields.put("serviceId", "Escolha um serviço.");
        }
        Optional<LocalDate> date = Schedule.parseDate(b.get("date"));
        if (date.isEmpty()) {
            fields.put("date", "Data inválida (use AAAA-MM-DD).");
        }
        Optional<LocalTime> time = Schedule.parseTime(b.get("time"));
        if (time.isEmpty()) {
            fields.put("time", "Horário inválido (use HH:mm).");
        }
        String name = b.get("customerName") instanceof String n ? n.strip().replaceAll("\\s+", " ") : "";
        if (name.length() < 2) {
            fields.put("customerName", "Informe seu nome.");
        } else if (name.length() > 80) {
            fields.put("customerName", "Nome muito longo.");
        }
        Optional<PhoneE164> phone = PhoneE164.parse(b.get("customerPhone"));
        if (phone.isEmpty()) {
            fields.put("customerPhone", PhoneE164.MESSAGE);
        }
        String notes = null;
        Object rawNotes = b.get("notes");
        if (rawNotes != null) {
            if (!(rawNotes instanceof String n)) {
                fields.put("notes", "Observação inválida.");
            } else if (n.strip().length() > 300) {
                fields.put("notes", "Use no máximo 300 caracteres.");
            } else {
                notes = n.strip().isEmpty() ? null : n.strip();
            }
        }
        if (!fields.isEmpty()) {
            throw new DomainValidationException(fields);
        }
        return new BookingInput((String) serviceId, date.get(), time.get(), name, phone.get(), notes);
    }

    /** Criação ({@code partial=false}, campos obrigatórios) ou edição parcial de serviço. */
    public static ServiceInput service(Map<String, Object> body, boolean partial) {
        Map<String, Object> b = body == null ? Map.of() : body;
        Map<String, String> fields = new LinkedHashMap<>();

        Optional<String> name = Optional.empty();
        if (b.containsKey("name") || !partial) {
            String n = b.get("name") instanceof String s ? s.strip() : "";
            if (n.length() < 2 || n.length() > 60) {
                fields.put("name", "Informe o nome do serviço (2 a 60 caracteres).");
            } else {
                name = Optional.of(n);
            }
        }
        Optional<String> categoryId = Optional.empty();
        if (b.containsKey("categoryId") || !partial) {
            if (!(b.get("categoryId") instanceof String c) || c.isEmpty()) {
                fields.put("categoryId", "Escolha a categoria.");
            } else {
                categoryId = Optional.of(c);
            }
        }
        Optional<Optional<String>> subcategory = Optional.empty();
        if (b.containsKey("subcategory")) {
            Object v = b.get("subcategory");
            if (v == null || "".equals(v)) {
                subcategory = Optional.of(Optional.empty());
            } else if (!(v instanceof String s) || s.strip().length() > 60) {
                fields.put("subcategory", "Subcategoria inválida.");
            } else {
                subcategory = Optional.of(Optional.of(s.strip()));
            }
        }
        Optional<Integer> duration = Optional.empty();
        if (b.containsKey("durationMin") || !partial) {
            String error = Duration30.validate(b.get("durationMin"));
            if (error != null) {
                fields.put("durationMin", error);
            } else {
                duration = Optional.of(((Number) b.get("durationMin")).intValue());
            }
        }
        Optional<Optional<Integer>> price = Optional.empty();
        if (b.containsKey("priceCents") || !partial) {
            Object v = b.get("priceCents");
            String error = Money.validateCents(v);
            if (error != null) {
                fields.put("priceCents", error);
            } else {
                price = Optional.of(v == null ? Optional.empty() : Optional.of(((Number) v).intValue()));
            }
        }
        Optional<String> professionalId = Optional.empty();
        if (b.containsKey("professionalId") || !partial) {
            if (!(b.get("professionalId") instanceof String p) || p.isEmpty()) {
                fields.put("professionalId", "Escolha a profissional.");
            } else {
                professionalId = Optional.of(p);
            }
        }
        Optional<Boolean> active = Optional.empty();
        if (b.containsKey("active")) {
            if (!(b.get("active") instanceof Boolean a)) {
                fields.put("active", "Valor inválido.");
            } else {
                active = Optional.of(a);
            }
        } else if (!partial) {
            active = Optional.of(true);
        }
        if (!fields.isEmpty()) {
            throw new DomainValidationException(fields);
        }
        return new ServiceInput(name, categoryId, subcategory, duration, price, professionalId, active);
    }

    /** Texto opcional: nulo ou vazio → nulo; acima do limite → erro no campo. */
    public static Optional<String> optionalText(Map<String, Object> b, String field, int max, String message,
                                                Map<String, String> fields) {
        Object v = b.get(field);
        if (v == null || "".equals(v)) {
            return Optional.empty();
        }
        if (!(v instanceof String s) || s.strip().length() > max) {
            fields.put(field, message);
            return Optional.empty();
        }
        return Optional.of(s.strip());
    }
}
