package com.longobuccodev.beautystudio.booking.core.domain;

import com.longobuccodev.beautystudio.booking.core.exception.InvalidTransitionException;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Objects;
import java.util.UUID;

/**
 * Agendamento. Serviço, profissional, preço e duração são cópias do momento do pedido:
 * mudanças posteriores no catálogo não alteram agendamentos existentes.
 */
public class Appointment {

    public static final int MAX_REASON = 300;

    private final UUID id;
    private final String serviceId;
    private final String professionalId;
    private final String serviceName;
    private final String professionalName;
    private final int durationMin;
    private final Integer priceCents;
    private final LocalDate date;
    private final LocalTime time;
    private final String customerName;
    private final String customerPhoneE164;
    private final String notes;
    private AppointmentStatus status;
    private final Instant createdAt;
    private Instant confirmedAt;
    private Instant rejectedAt;
    private Instant cancelledAt;
    private String reason;
    private Instant seenByAdminAt;

    public Appointment(UUID id, String serviceId, String professionalId, String serviceName, String professionalName,
                       int durationMin, Integer priceCents, LocalDate date, LocalTime time, String customerName,
                       String customerPhoneE164, String notes, AppointmentStatus status, Instant createdAt,
                       Instant confirmedAt, Instant rejectedAt, Instant cancelledAt, String reason,
                       Instant seenByAdminAt) {
        this.id = Objects.requireNonNull(id);
        this.serviceId = serviceId;
        this.professionalId = professionalId;
        this.serviceName = serviceName;
        this.professionalName = professionalName;
        this.durationMin = durationMin;
        this.priceCents = priceCents;
        this.date = date;
        this.time = time;
        this.customerName = customerName;
        this.customerPhoneE164 = customerPhoneE164;
        this.notes = notes;
        this.status = status;
        this.createdAt = createdAt;
        this.confirmedAt = confirmedAt;
        this.rejectedAt = rejectedAt;
        this.cancelledAt = cancelledAt;
        this.reason = reason;
        this.seenByAdminAt = seenByAdminAt;
    }

    /** Novo pedido, aguardando confirmação, com preço e duração vigentes no serviço. */
    public static Appointment request(SalonService service, Professional professional, LocalDate date, LocalTime time,
                                      String customerName, PhoneE164 phone, String notes, Instant now) {
        return new Appointment(UUID.randomUUID(), service.id(), service.professionalId(), service.name(),
                professional == null ? "" : professional.name(), service.durationMin(), service.priceCents(),
                date, time, customerName, phone.value(), notes, AppointmentStatus.PENDING, now,
                null, null, null, null, null);
    }

    /**
     * Aplica a ação. O motivo é guardado só quando informado, limitado a 300 caracteres.
     * Mudança de status pelo painel conta como pedido visto.
     */
    public StatusChange apply(StatusAction action, String reason, Instant at, String actor) {
        AppointmentStatus to = action.next(status).orElseThrow(InvalidTransitionException::new);
        AppointmentStatus from = status;
        status = to;
        switch (to) {
            case CONFIRMED -> confirmedAt = at;
            case REJECTED -> rejectedAt = at;
            case CANCELLED -> cancelledAt = at;
            default -> {
            }
        }
        String trimmed = reason == null ? null : reason.strip();
        if (trimmed != null && !trimmed.isEmpty()) {
            this.reason = trimmed.length() > MAX_REASON ? trimmed.substring(0, MAX_REASON) : trimmed;
        }
        if (seenByAdminAt == null) {
            seenByAdminAt = at;
        }
        return new StatusChange(id, from, to, at, actor, this.reason);
    }

    public void markSeen(Instant at) {
        if (status == AppointmentStatus.PENDING && seenByAdminAt == null) {
            seenByAdminAt = at;
        }
    }

    public int startMinutes() {
        return Schedule.toMinutes(time);
    }

    public int endMinutes() {
        return startMinutes() + durationMin;
    }

    public OffsetDateTime start() {
        return Schedule.at(date, startMinutes());
    }

    public OffsetDateTime end() {
        return Schedule.at(date, endMinutes());
    }

    /** Mesma profissional, mesmo dia e intervalos sobrepostos (outro agendamento). */
    public boolean overlaps(Appointment other) {
        return !id.equals(other.id) && professionalId.equals(other.professionalId) && date.equals(other.date)
                && Schedule.overlaps(startMinutes(), endMinutes(), other.startMinutes(), other.endMinutes());
    }

    /** Conflito do pedido pendente frente aos demais; agendamento não pendente nunca tem conflito. */
    public ConflictKind conflictWith(Collection<Appointment> all) {
        if (status != AppointmentStatus.PENDING) {
            return null;
        }
        boolean pending = false;
        for (Appointment other : all) {
            if (!overlaps(other)) {
                continue;
            }
            if (other.status == AppointmentStatus.CONFIRMED) {
                return ConflictKind.CONFIRMED;
            }
            pending |= other.status == AppointmentStatus.PENDING;
        }
        return pending ? ConflictKind.PENDING : null;
    }

    public Availability.Busy asBusy() {
        return new Availability.Busy(date, time, durationMin);
    }

    public String customerFirstName() {
        return customerName.split(" ")[0];
    }

    public UUID id() { return id; }
    public String serviceId() { return serviceId; }
    public String professionalId() { return professionalId; }
    public String serviceName() { return serviceName; }
    public String professionalName() { return professionalName; }
    public int durationMin() { return durationMin; }
    public Integer priceCents() { return priceCents; }
    public LocalDate date() { return date; }
    public LocalTime time() { return time; }
    public String customerName() { return customerName; }
    public String customerPhoneE164() { return customerPhoneE164; }
    public String notes() { return notes; }
    public AppointmentStatus status() { return status; }
    public Instant createdAt() { return createdAt; }
    public Instant confirmedAt() { return confirmedAt; }
    public Instant rejectedAt() { return rejectedAt; }
    public Instant cancelledAt() { return cancelledAt; }
    public String reason() { return reason; }
    public Instant seenByAdminAt() { return seenByAdminAt; }
}
