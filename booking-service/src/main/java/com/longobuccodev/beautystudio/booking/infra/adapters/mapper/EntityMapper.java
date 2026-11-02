package com.longobuccodev.beautystudio.booking.infra.adapters.mapper;

import com.longobuccodev.beautystudio.booking.core.domain.Account;
import com.longobuccodev.beautystudio.booking.core.domain.AccountRole;
import com.longobuccodev.beautystudio.booking.core.domain.Appointment;
import com.longobuccodev.beautystudio.booking.core.domain.AppointmentStatus;
import com.longobuccodev.beautystudio.booking.core.domain.Category;
import com.longobuccodev.beautystudio.booking.core.domain.LoginSource;
import com.longobuccodev.beautystudio.booking.core.domain.Professional;
import com.longobuccodev.beautystudio.booking.core.domain.SalonService;
import com.longobuccodev.beautystudio.booking.infra.entities.AccountEntity;
import com.longobuccodev.beautystudio.booking.infra.entities.AppointmentEntity;
import com.longobuccodev.beautystudio.booking.infra.entities.CategoryEntity;
import com.longobuccodev.beautystudio.booking.infra.entities.ProfessionalEntity;
import com.longobuccodev.beautystudio.booking.infra.entities.ServiceEntity;

import java.time.Instant;

/** Entidade JPA ↔ domínio. */
public final class EntityMapper {

    private EntityMapper() {
    }

    public static Professional toDomain(ProfessionalEntity e) {
        return new Professional(e.getId(), e.getName(), e.isActive(), e.getWhatsappPhone());
    }

    public static Category toDomain(CategoryEntity e) {
        return new Category(e.getId(), e.getName(), e.getNote(), e.getSortOrder());
    }

    public static CategoryEntity toEntity(Category c) {
        return new CategoryEntity(c.id(), c.name(), c.note(), c.sortOrder());
    }

    public static SalonService toDomain(ServiceEntity e) {
        return new SalonService(e.getId(), e.getName(), e.getCategoryId(), e.getSubcategory(), e.getDurationMin(),
                e.isDurationEstimated(), e.getPriceCents(), e.getProfessionalId(), e.isActive(), e.getSortOrder(),
                e.getPhotoKey(), e.getPhotoContentType());
    }

    public static ServiceEntity toEntity(SalonService s) {
        return new ServiceEntity(s.id(), s.name(), s.categoryId(), s.subcategory(), s.durationMin(),
                s.durationEstimated(), s.priceCents(), s.professionalId(), s.active(), s.sortOrder(), s.photoKey(),
                s.photoContentType());
    }

    public static Appointment toDomain(AppointmentEntity e) {
        return new Appointment(e.getId(), e.getServiceId(), e.getProfessionalId(), e.getServiceName(),
                e.getProfessionalName(), e.getDurationMin(), e.getPriceCents(), e.getDate(), e.getTime(),
                e.getCustomerName(), e.getCustomerPhone(), e.getNotes(),
                AppointmentStatus.of(e.getStatus()).orElseThrow(), e.getCreatedAt(), e.getConfirmedAt(),
                e.getRejectedAt(), e.getCancelledAt(), e.getReason(), e.getSeenAt());
    }

    public static AppointmentEntity toEntity(Appointment a) {
        return new AppointmentEntity(a.id(), a.serviceId(), a.professionalId(), a.serviceName(),
                a.professionalName(), a.durationMin(), a.priceCents(), a.date(), a.time(), a.start(), a.end(),
                a.customerName(), a.customerPhoneE164(), a.notes(), a.status().value(), a.createdAt(),
                a.confirmedAt(), a.rejectedAt(), a.cancelledAt(), a.reason(), a.seenByAdminAt());
    }

    public static Account toDomain(AccountEntity e) {
        return new Account(e.getId(), e.getUsername(),
                AccountRole.MASTER.value().equals(e.getRole()) ? AccountRole.MASTER : AccountRole.PROFESSIONAL,
                e.getProfessionalId(), e.getPasswordHash(), e.getPasswordVersion(),
                LoginSource.ENV.value().equals(e.getSource()) ? LoginSource.ENV : LoginSource.DB);
    }

    public static AccountEntity toEntity(Account a, Instant now) {
        return new AccountEntity(a.id(), a.username(), a.role().value(), a.professionalId(), a.passwordHash(),
                a.passwordVersion(), a.source().value(), now);
    }
}
