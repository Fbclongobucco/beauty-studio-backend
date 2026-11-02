package com.longobuccodev.beautystudio.booking.application.usecase;

import com.longobuccodev.beautystudio.booking.application.dto.Dtos.EarningsDto;
import com.longobuccodev.beautystudio.booking.application.mapper.DtoMapper;
import com.longobuccodev.beautystudio.booking.application.security.AccessScope;
import com.longobuccodev.beautystudio.booking.application.security.AdminPrincipal;
import com.longobuccodev.beautystudio.booking.core.domain.AppointmentStatus;
import com.longobuccodev.beautystudio.booking.core.domain.Earnings;
import com.longobuccodev.beautystudio.booking.core.exception.DomainValidationException;
import com.longobuccodev.beautystudio.booking.core.repository.AppointmentRepository;

import java.time.Clock;
import java.time.LocalDate;

/** Ganhos do período (spec admin-earnings do front), da profissional ou do salão. */
public class EarningsUseCase {

    private final AppointmentRepository appointments;
    private final Clock clock;

    public EarningsUseCase(AppointmentRepository appointments, Clock clock) {
        this.appointments = appointments;
        this.clock = clock;
    }

    public EarningsDto earnings(AdminPrincipal principal, String from, String to, String professionalId) {
        Earnings.validatePeriod(from, to).ifPresent(fields -> {
            throw new DomainValidationException(fields);
        });
        LocalDate f = LocalDate.parse(from);
        LocalDate t = LocalDate.parse(to);
        var list = appointments.find(new AppointmentRepository.Filter(AppointmentStatus.CONFIRMED, null, f, t,
                AccessScope.professionalFilter(principal, professionalId)));
        return DtoMapper.toDto(Earnings.compute(list, f, t, clock.instant()));
    }
}
