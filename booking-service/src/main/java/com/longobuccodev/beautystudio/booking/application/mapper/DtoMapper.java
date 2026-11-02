package com.longobuccodev.beautystudio.booking.application.mapper;

import com.longobuccodev.beautystudio.booking.application.dto.Dtos.AdminAppointmentDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.AppointmentDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.BucketDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.ByDayDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.ByProfessionalDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.ByServiceDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.CategoryDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.EarningsDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.ProfessionalDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.PublicAppointmentDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.ServiceDto;
import com.longobuccodev.beautystudio.booking.core.domain.Appointment;
import com.longobuccodev.beautystudio.booking.core.domain.Category;
import com.longobuccodev.beautystudio.booking.core.domain.ConflictKind;
import com.longobuccodev.beautystudio.booking.core.domain.Earnings;
import com.longobuccodev.beautystudio.booking.core.domain.Professional;
import com.longobuccodev.beautystudio.booking.core.domain.SalonService;
import com.longobuccodev.beautystudio.booking.core.domain.Schedule;
import com.longobuccodev.beautystudio.booking.core.storage.PhotoStorage;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** Domínio → JSON do contrato do front. */
public final class DtoMapper {

    private DtoMapper() {
    }

    public static ServiceDto toDto(SalonService s, PhotoStorage storage) {
        return new ServiceDto(s.id(), s.name(), s.categoryId(), s.subcategory(), s.durationMin(),
                s.durationEstimated(), s.priceCents(), s.professionalId(), s.active(), s.sortOrder(),
                storage.publicUrl(s.photoKey()));
    }

    public static CategoryDto toDto(Category c) {
        return new CategoryDto(c.id(), c.name(), c.note(), c.sortOrder());
    }

    public static ProfessionalDto toDto(Professional p) {
        return new ProfessionalDto(p.id(), p.name(), p.active(), p.whatsappPhone());
    }

    public static AppointmentDto toDto(Appointment a) {
        return new AppointmentDto(a.id().toString(), a.serviceId(), a.professionalId(), a.serviceName(),
                a.professionalName(), a.durationMin(), a.priceCents(), a.date().toString(),
                Schedule.formatTime(a.time()), Schedule.formatIso(a.start()), Schedule.formatIso(a.end()),
                a.customerName(), a.customerPhoneE164(), a.notes(), a.status().value(), iso(a.createdAt()),
                iso(a.confirmedAt()), iso(a.rejectedAt()), iso(a.cancelledAt()), a.reason(), iso(a.seenByAdminAt()));
    }

    public static AdminAppointmentDto toAdminDto(Appointment a, ConflictKind conflict) {
        AppointmentDto d = toDto(a);
        return new AdminAppointmentDto(d.id(), d.serviceId(), d.professionalId(), d.serviceName(),
                d.professionalName(), d.durationMin(), d.priceCents(), d.date(), d.time(), d.startISO(), d.endISO(),
                d.customerName(), d.customerPhoneE164(), d.notes(), d.status(), d.createdAt(), d.confirmedAt(),
                d.rejectedAt(), d.cancelledAt(), d.reason(), d.seenByAdminAt(),
                conflict == null ? null : conflict.value());
    }

    /** Status público: só o primeiro nome da cliente, sem telefone. */
    public static PublicAppointmentDto toPublicDto(Appointment a) {
        return new PublicAppointmentDto(a.id().toString(), a.status().value(), a.serviceName(), a.professionalName(),
                a.durationMin(), a.priceCents(), a.date().toString(), Schedule.formatTime(a.time()),
                Schedule.formatIso(a.start()), Schedule.formatIso(a.end()), a.customerFirstName());
    }

    public static EarningsDto toDto(Earnings.Report r) {
        return new EarningsDto(r.from().toString(), r.to().toString(), bucket(r.total()), bucket(r.realized()),
                bucket(r.expected()), r.unpriced(),
                r.byService().stream().map(s -> new ByServiceDto(s.name(), s.count(), s.cents())).toList(),
                r.byDay().stream().map(d -> new ByDayDto(d.date().toString(), d.realizedCents(), d.expectedCents()))
                        .toList(),
                r.byProfessional().stream()
                        .map(p -> new ByProfessionalDto(p.professionalId(), p.name(), p.count(), p.cents())).toList());
    }

    private static BucketDto bucket(Earnings.Bucket b) {
        return new BucketDto(b.cents(), b.count());
    }

    /** ISO 8601 em UTC com milissegundos, como {@code Date.toISOString()} da API de teste. */
    public static String iso(Instant instant) {
        return instant == null ? null : instant.truncatedTo(ChronoUnit.MILLIS).toString();
    }
}
