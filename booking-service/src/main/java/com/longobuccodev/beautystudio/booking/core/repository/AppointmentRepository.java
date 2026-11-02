package com.longobuccodev.beautystudio.booking.core.repository;

import com.longobuccodev.beautystudio.booking.core.domain.Appointment;
import com.longobuccodev.beautystudio.booking.core.domain.AppointmentStatus;
import com.longobuccodev.beautystudio.booking.core.domain.StatusChange;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository {

    /**
     * Filtros opcionais (nulos não filtram). {@code from} e {@code to} são inclusivos.
     * Ordem: data, horário e criação.
     */
    record Filter(AppointmentStatus status, LocalDate date, LocalDate from, LocalDate to, String professionalId) {
    }

    List<Appointment> find(Filter filter);

    Optional<Appointment> findById(UUID id);

    /** Busca com bloqueio da linha até o fim da transação (transições de status). */
    Optional<Appointment> findByIdForUpdate(UUID id);

    /**
     * Serializa, até o fim da transação, as mudanças que ocupam a agenda da profissional (confirmações).
     * Evita que duas confirmações simultâneas se bloqueiem mutuamente na regra de sobreposição do banco.
     */
    void lockSchedule(String professionalId);

    /** Pendentes e confirmados das profissionais no intervalo de datas (cálculo de conflito). */
    List<Appointment> findActive(Collection<String> professionalIds, LocalDate from, LocalDate to);

    /** Confirmados da profissional a partir da data (disponibilidade). */
    List<Appointment> findConfirmed(String professionalId, LocalDate from);

    /**
     * Grava o agendamento. Dois confirmados sobrepostos da mesma profissional violam a regra do banco e
     * resultam em {@link com.longobuccodev.beautystudio.booking.core.exception.SlotTakenException}.
     */
    Appointment save(Appointment appointment);

    void appendHistory(StatusChange change);

    /** Pendentes (total e não vistos); {@code professionalId} nulo = salão todo. */
    PendingCount pendingCount(String professionalId);

    record PendingCount(long pending, long unseen) {
    }
}
