package com.longobuccodev.beautystudio.booking.application.dto;

import java.util.List;

/**
 * Formatos JSON do contrato HTTP do front (beauty-studio/lib/domain/types.ts).
 * Datas como texto: AAAA-MM-DD, HH:mm e ISO 8601.
 */
public final class Dtos {

    private Dtos() {
    }

    // ---- Catálogo ----

    public record ServiceDto(String id, String name, String categoryId, String subcategory, int durationMin,
                             boolean durationEstimated, Integer priceCents, String professionalId, boolean active,
                             int sortOrder, String photoUrl) {
    }

    public record ProfessionalRef(String id, String name) {
    }

    public record PublicServiceDto(String id, String name, String subcategory, int durationMin, Integer priceCents,
                                   ProfessionalRef professional, String photoUrl) {
    }

    public record PublicCategoryDto(String id, String name, String note, List<PublicServiceDto> services) {
    }

    public record ServicesResponse(List<PublicCategoryDto> categories) {
    }

    public record CategoryDto(String id, String name, String note, int sortOrder) {
    }

    public record CategoriesResponse(List<CategoryDto> categories) {
    }

    /** {@code whatsappPhone} só nas respostas do painel; nunca nos endpoints públicos. */
    public record ProfessionalDto(String id, String name, boolean active, String whatsappPhone) {
    }

    public record ProfessionalsResponse(List<ProfessionalDto> professionals) {
    }

    public record AdminCatalogResponse(List<ServiceDto> services, List<CategoryDto> categories,
                                       List<ProfessionalDto> professionals) {
    }

    // ---- Disponibilidade ----

    /** {@code taken}: horários da grade ocupados por confirmado (o front mostra em cinza). */
    public record AvailabilityResponse(String serviceId, String date, List<String> slots, List<String> taken) {
    }

    public record DayDto(String date, boolean open, boolean hasSlots) {
    }

    public record DaysResponse(String serviceId, List<DayDto> days) {
    }

    // ---- Agendamentos ----

    public record AppointmentDto(String id, String serviceId, String professionalId, String serviceName,
                                 String professionalName, int durationMin, Integer priceCents, String date,
                                 String time, String startISO, String endISO, String customerName,
                                 String customerPhoneE164, String notes, String status, String createdAt,
                                 String confirmedAt, String rejectedAt, String cancelledAt, String reason,
                                 String seenByAdminAt) {
    }

    /** Agendamento do painel, com {@code conflict}: "confirmed", "pending" ou nulo. */
    public record AdminAppointmentDto(String id, String serviceId, String professionalId, String serviceName,
                                      String professionalName, int durationMin, Integer priceCents, String date,
                                      String time, String startISO, String endISO, String customerName,
                                      String customerPhoneE164, String notes, String status, String createdAt,
                                      String confirmedAt, String rejectedAt, String cancelledAt, String reason,
                                      String seenByAdminAt, String conflict) {
    }

    public record AppointmentsResponse(List<AdminAppointmentDto> appointments) {
    }

    public record PublicAppointmentDto(String id, String status, String serviceName, String professionalName,
                                       int durationMin, Integer priceCents, String date, String time,
                                       String startISO, String endISO, String customerFirstName) {
    }

    public record PendingCountDto(long pending, long unseen) {
    }

    public record MarkedDto(int marked) {
    }

    // ---- Sessão e contas ----

    public record OkDto(boolean ok) {
    }

    /** Conta logada. Master: {@code professionalId} nulo, {@code name} vazio e {@code whatsappPhone} nulo. */
    public record SessionDto(String role, String professionalId, String name, String whatsappPhone) {
    }

    /** {@code login}: "env", "db" ou nulo. */
    public record AccountDto(String professionalId, String name, String login) {
    }

    public record AccountsResponse(String masterUsername, List<AccountDto> accounts) {
    }

    // ---- Ganhos (valores em centavos) ----

    public record BucketDto(long cents, int count) {
    }

    public record ByServiceDto(String name, int count, long cents) {
    }

    public record ByDayDto(String date, long realizedCents, long expectedCents) {
    }

    public record ByProfessionalDto(String professionalId, String name, int count, long cents) {
    }

    public record EarningsDto(String from, String to, BucketDto total, BucketDto realized, BucketDto expected,
                              int unpriced, List<ByServiceDto> byService, List<ByDayDto> byDay,
                              List<ByProfessionalDto> byProfessional) {
    }
}
