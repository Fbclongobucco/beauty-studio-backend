package com.longobuccodev.beautystudio.booking.application.usecase;

import com.longobuccodev.beautystudio.booking.application.dto.Dtos.AvailabilityResponse;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.DayDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.DaysResponse;
import com.longobuccodev.beautystudio.booking.application.exception.ResourceNotFoundException;
import com.longobuccodev.beautystudio.booking.core.domain.Appointment;
import com.longobuccodev.beautystudio.booking.core.domain.Availability;
import com.longobuccodev.beautystudio.booking.core.domain.SalonService;
import com.longobuccodev.beautystudio.booking.core.domain.Schedule;
import com.longobuccodev.beautystudio.booking.core.exception.DomainValidationException;
import com.longobuccodev.beautystudio.booking.core.repository.AppointmentRepository;
import com.longobuccodev.beautystudio.booking.core.repository.ServiceRepository;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Horários livres por serviço, considerando só confirmados da profissional responsável. */
public class AvailabilityUseCase {

    private final ServiceRepository services;
    private final AppointmentRepository appointments;
    private final Clock clock;

    public AvailabilityUseCase(ServiceRepository services, AppointmentRepository appointments, Clock clock) {
        this.services = services;
        this.appointments = appointments;
        this.clock = clock;
    }

    public AvailabilityResponse slots(String serviceId, String date) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (serviceId == null || serviceId.isEmpty()) {
            fields.put("serviceId", "Informe o serviço.");
        }
        Optional<LocalDate> d = Schedule.parseDate(date);
        if (d.isEmpty()) {
            fields.put("date", "Data inválida (use AAAA-MM-DD).");
        }
        if (!fields.isEmpty()) {
            throw new DomainValidationException(fields);
        }
        SalonService svc = activeService(serviceId);
        return new AvailabilityResponse(serviceId, date,
                Availability.availableSlots(svc.durationMin(), d.get(), busy(svc, d.get()), clock));
    }

    public DaysResponse days(String serviceId, String from, String days) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (serviceId == null || serviceId.isEmpty()) {
            fields.put("serviceId", "Informe o serviço.");
        }
        Optional<LocalDate> f = Schedule.parseDate(from);
        if (f.isEmpty()) {
            fields.put("from", "Data inválida (use AAAA-MM-DD).");
        }
        Integer n = parseDays(days);
        if (n == null) {
            fields.put("days", "Informe de 1 a 62 dias.");
        }
        if (!fields.isEmpty()) {
            throw new DomainValidationException(fields);
        }
        SalonService svc = activeService(serviceId);
        List<DayDto> out = Availability.availableDays(svc.durationMin(), f.get(), n, busy(svc, f.get()), clock)
                .stream().map(i -> new DayDto(i.date().toString(), i.open(), i.hasSlots())).toList();
        return new DaysResponse(serviceId, out);
    }

    private SalonService activeService(String id) {
        return services.findById(id).filter(SalonService::active)
                .orElseThrow(() -> new ResourceNotFoundException("Serviço não encontrado."));
    }

    private List<Availability.Busy> busy(SalonService svc, LocalDate from) {
        return appointments.findConfirmed(svc.professionalId(), from).stream().map(Appointment::asBusy).toList();
    }

    /** Padrão: janela inteira (31 dias). */
    private static Integer parseDays(String value) {
        if (value == null) {
            return Schedule.WINDOW_DAYS + 1;
        }
        try {
            int n = Integer.parseInt(value.strip());
            return n >= 1 && n <= 62 ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
