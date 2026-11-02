package com.longobuccodev.beautystudio.booking.application.usecase;

import com.longobuccodev.beautystudio.booking.application.dto.Dtos.AppointmentDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.PublicAppointmentDto;
import com.longobuccodev.beautystudio.booking.application.exception.ResourceNotFoundException;
import com.longobuccodev.beautystudio.booking.application.mapper.DtoMapper;
import com.longobuccodev.beautystudio.booking.application.port.TransactionRunner;
import com.longobuccodev.beautystudio.booking.application.validation.InputValidators;
import com.longobuccodev.beautystudio.booking.application.validation.InputValidators.BookingInput;
import com.longobuccodev.beautystudio.booking.core.domain.Appointment;
import com.longobuccodev.beautystudio.booking.core.domain.Availability;
import com.longobuccodev.beautystudio.booking.core.domain.Professional;
import com.longobuccodev.beautystudio.booking.core.domain.SalonService;
import com.longobuccodev.beautystudio.booking.core.event.AppointmentEventKind;
import com.longobuccodev.beautystudio.booking.core.event.EventRecorder;
import com.longobuccodev.beautystudio.booking.core.exception.DomainValidationException;
import com.longobuccodev.beautystudio.booking.core.exception.SlotUnavailableException;
import com.longobuccodev.beautystudio.booking.core.repository.AppointmentRepository;
import com.longobuccodev.beautystudio.booking.core.repository.ProfessionalRepository;
import com.longobuccodev.beautystudio.booking.core.repository.ServiceRepository;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Pedido de agendamento pela cliente e consulta pública do status. */
public class BookingUseCase {

    static final String APPOINTMENT_NOT_FOUND = "Agendamento não encontrado.";

    private final ServiceRepository services;
    private final ProfessionalRepository professionals;
    private final AppointmentRepository appointments;
    private final EventRecorder events;
    private final TransactionRunner tx;
    private final Clock clock;

    public BookingUseCase(ServiceRepository services, ProfessionalRepository professionals,
                          AppointmentRepository appointments, EventRecorder events, TransactionRunner tx,
                          Clock clock) {
        this.services = services;
        this.professionals = professionals;
        this.appointments = appointments;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    /**
     * Cria o pedido "Aguardando confirmação" com preço e duração vigentes e grava {@code appointment.created}
     * na mesma transação. O WhatsApp informado pela cliente é o destino dos avisos de status.
     */
    public AppointmentDto create(Map<String, Object> body) {
        BookingInput in = InputValidators.booking(body);
        return tx.inTransaction(() -> {
            SalonService svc = services.findById(in.serviceId()).filter(SalonService::active)
                    .orElseThrow(() -> DomainValidationException.field("serviceId", "Esse serviço não está disponível."));
            List<Availability.Busy> busy = appointments.findConfirmed(svc.professionalId(), in.date()).stream()
                    .map(Appointment::asBusy).toList();
            if (!Availability.isSlotAvailable(svc.durationMin(), in.date(), in.time(), busy, clock)) {
                throw new SlotUnavailableException();
            }
            Professional professional = professionals.findById(svc.professionalId()).orElse(null);
            Appointment created = appointments.save(Appointment.request(svc, professional, in.date(), in.time(),
                    in.customerName(), in.customerPhone(), in.notes(), clock.instant()));
            events.record(AppointmentEventKind.CREATED, created, professional);
            return DtoMapper.toDto(created);
        });
    }

    /** Status público (link enviado à cliente), sem dados de outras clientes. */
    public PublicAppointmentDto publicStatus(String id) {
        return parseId(id).flatMap(appointments::findById).map(DtoMapper::toPublicDto)
                .orElseThrow(() -> new ResourceNotFoundException(APPOINTMENT_NOT_FOUND));
    }

    static java.util.Optional<UUID> parseId(String id) {
        try {
            return java.util.Optional.of(UUID.fromString(id));
        } catch (IllegalArgumentException | NullPointerException e) {
            return java.util.Optional.empty();
        }
    }
}
