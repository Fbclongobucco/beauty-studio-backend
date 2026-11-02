package com.longobuccodev.beautystudio.booking.application.usecase;

import com.longobuccodev.beautystudio.booking.application.dto.Dtos.AdminAppointmentDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.AppointmentDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.AppointmentsResponse;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.MarkedDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.PendingCountDto;
import com.longobuccodev.beautystudio.booking.application.exception.ResourceNotFoundException;
import com.longobuccodev.beautystudio.booking.application.mapper.DtoMapper;
import com.longobuccodev.beautystudio.booking.application.port.TransactionRunner;
import com.longobuccodev.beautystudio.booking.application.security.AccessScope;
import com.longobuccodev.beautystudio.booking.application.security.AdminPrincipal;
import com.longobuccodev.beautystudio.booking.core.domain.Appointment;
import com.longobuccodev.beautystudio.booking.core.domain.AppointmentStatus;
import com.longobuccodev.beautystudio.booking.core.domain.Schedule;
import com.longobuccodev.beautystudio.booking.core.domain.StatusAction;
import com.longobuccodev.beautystudio.booking.core.domain.StatusChange;
import com.longobuccodev.beautystudio.booking.core.event.AppointmentEventKind;
import com.longobuccodev.beautystudio.booking.core.event.EventRecorder;
import com.longobuccodev.beautystudio.booking.core.exception.DomainValidationException;
import com.longobuccodev.beautystudio.booking.core.exception.InvalidTransitionException;
import com.longobuccodev.beautystudio.booking.core.exception.SlotTakenException;
import com.longobuccodev.beautystudio.booking.core.repository.AppointmentRepository;
import com.longobuccodev.beautystudio.booking.core.repository.ProfessionalRepository;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Pedidos e agenda no painel (spec admin-appointments do front), sempre no escopo da sessão. */
public class AdminAppointmentUseCase {

    private final AppointmentRepository appointments;
    private final ProfessionalRepository professionals;
    private final EventRecorder events;
    private final TransactionRunner tx;
    private final Clock clock;

    public AdminAppointmentUseCase(AppointmentRepository appointments, ProfessionalRepository professionals,
                                   EventRecorder events, TransactionRunner tx, Clock clock) {
        this.appointments = appointments;
        this.professionals = professionals;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    /** Lista filtrada com {@code conflict}. Para a profissional, o filtro de profissional é ignorado. */
    public AppointmentsResponse list(AdminPrincipal principal, String status, String date, String from, String to,
                                     String professionalId) {
        Map<String, String> fields = new LinkedHashMap<>();
        Optional<AppointmentStatus> s = blank(status) ? Optional.empty() : AppointmentStatus.of(status);
        if (!blank(status) && s.isEmpty()) {
            fields.put("status", "Status inválido.");
        }
        LocalDate d = optionalDate("date", date, fields);
        LocalDate f = optionalDate("from", from, fields);
        LocalDate t = optionalDate("to", to, fields);
        if (!fields.isEmpty()) {
            throw new DomainValidationException(fields);
        }
        List<Appointment> list = appointments.find(new AppointmentRepository.Filter(s.orElse(null), d, f, t,
                AccessScope.professionalFilter(principal, professionalId)));
        List<Appointment> context = conflictContext(list);
        List<AdminAppointmentDto> out = list.stream()
                .map(a -> DtoMapper.toAdminDto(a, a.conflictWith(context))).toList();
        return new AppointmentsResponse(out);
    }

    /** Pendentes e confirmados das mesmas profissionais e dias dos pendentes listados. */
    private List<Appointment> conflictContext(List<Appointment> list) {
        List<Appointment> pending = list.stream().filter(a -> a.status() == AppointmentStatus.PENDING).toList();
        if (pending.isEmpty()) {
            return List.of();
        }
        Set<String> profs = pending.stream().map(Appointment::professionalId).collect(Collectors.toSet());
        LocalDate min = pending.stream().map(Appointment::date).min(Comparator.naturalOrder()).orElseThrow();
        LocalDate max = pending.stream().map(Appointment::date).max(Comparator.naturalOrder()).orElseThrow();
        return appointments.findActive(profs, min, max);
    }

    /** Pendentes da profissional, ou do salão todo para o master. */
    public PendingCountDto pendingCount(AdminPrincipal principal) {
        AppointmentRepository.PendingCount c = appointments.pendingCount(
                AccessScope.professionalFilter(principal, null));
        return new PendingCountDto(c.pending(), c.unseen());
    }

    /** Só a profissional dona marca como visto; a visita do master não tira a marca "Novo". */
    public MarkedDto markSeen(AdminPrincipal principal, Map<String, Object> body) {
        if (principal.isMaster()) {
            return new MarkedDto(0);
        }
        Set<String> ids = body != null && body.get("ids") instanceof List<?> raw
                ? raw.stream().filter(String.class::isInstance).map(String.class::cast).collect(Collectors.toSet())
                : null;
        return tx.inTransaction(() -> {
            Instant now = clock.instant();
            int marked = 0;
            for (Appointment a : appointments.find(new AppointmentRepository.Filter(AppointmentStatus.PENDING,
                    null, null, null, principal.professionalId()))) {
                if (a.seenByAdminAt() != null || (ids != null && !ids.contains(a.id().toString()))) {
                    continue;
                }
                a.markSeen(now);
                appointments.save(a);
                marked++;
            }
            return new MarkedDto(marked);
        });
    }

    /**
     * Confirma, recusa ou cancela. Pedido alheio: 404. A linha fica bloqueada durante a transação, e o banco
     * impede dois confirmados sobrepostos mesmo entre instâncias: a segunda confirmação recebe SLOT_TAKEN.
     */
    public AppointmentDto transition(AdminPrincipal principal, String id, String actionValue,
                                     Map<String, Object> body) {
        StatusAction action = StatusAction.of(actionValue)
                .orElseThrow(() -> new ResourceNotFoundException("Ação desconhecida."));
        UUID uuid = BookingUseCase.parseId(id)
                .orElseThrow(() -> new ResourceNotFoundException(BookingUseCase.APPOINTMENT_NOT_FOUND));
        String reason = body != null && body.get("reason") instanceof String r && !r.isBlank() ? r.strip() : null;
        return tx.inTransaction(() -> {
            Appointment a = appointments.findByIdForUpdate(uuid)
                    .filter(x -> AccessScope.canAccess(principal, x.professionalId()))
                    .orElseThrow(() -> new ResourceNotFoundException(BookingUseCase.APPOINTMENT_NOT_FOUND));
            if (action.next(a.status()).isEmpty()) {
                throw new InvalidTransitionException();
            }
            if (action == StatusAction.CONFIRM) {
                // Confirmações da mesma profissional em fila: a segunda vê a primeira já gravada
                appointments.lockSchedule(a.professionalId());
            }
            if (action == StatusAction.CONFIRM && appointments.findActive(Set.of(a.professionalId()), a.date(),
                    a.date()).stream().anyMatch(o -> o.status() == AppointmentStatus.CONFIRMED && a.overlaps(o))) {
                throw new SlotTakenException();
            }
            StatusChange change = a.apply(action, reason, clock.instant(), principal.username());
            Appointment saved = appointments.save(a);
            appointments.appendHistory(change);
            events.record(AppointmentEventKind.of(saved.status()), saved,
                    professionals.findById(saved.professionalId()).orElse(null));
            return DtoMapper.toDto(saved);
        });
    }

    private static boolean blank(String s) {
        return s == null || s.isEmpty();
    }

    private static LocalDate optionalDate(String field, String value, Map<String, String> fields) {
        if (blank(value)) {
            return null;
        }
        Optional<LocalDate> d = Schedule.parseDate(value);
        if (d.isEmpty()) {
            fields.put(field, "Data inválida (use AAAA-MM-DD).");
            return null;
        }
        return d.get();
    }
}
