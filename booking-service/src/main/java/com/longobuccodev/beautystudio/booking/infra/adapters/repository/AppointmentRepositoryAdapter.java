package com.longobuccodev.beautystudio.booking.infra.adapters.repository;

import com.longobuccodev.beautystudio.booking.core.domain.Appointment;
import com.longobuccodev.beautystudio.booking.core.domain.AppointmentStatus;
import com.longobuccodev.beautystudio.booking.core.domain.StatusChange;
import com.longobuccodev.beautystudio.booking.core.exception.SlotTakenException;
import com.longobuccodev.beautystudio.booking.core.repository.AppointmentRepository;
import com.longobuccodev.beautystudio.booking.infra.adapters.mapper.EntityMapper;
import com.longobuccodev.beautystudio.booking.infra.entities.AppointmentEntity;
import com.longobuccodev.beautystudio.booking.infra.entities.AppointmentStatusHistoryEntity;
import com.longobuccodev.beautystudio.booking.infra.repositories.AppointmentJpaRepository;
import com.longobuccodev.beautystudio.booking.infra.repositories.AppointmentStatusHistoryJpaRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
public class AppointmentRepositoryAdapter implements AppointmentRepository {

    /** SQLState de violação de exclusion constraint no PostgreSQL. */
    static final String EXCLUSION_VIOLATION = "23P01";
    private static final Sort ORDER = Sort.by("date", "time", "createdAt");

    private final AppointmentJpaRepository jpa;
    private final AppointmentStatusHistoryJpaRepository history;
    private final JdbcClient jdbc;

    public AppointmentRepositoryAdapter(AppointmentJpaRepository jpa, AppointmentStatusHistoryJpaRepository history,
                                        JdbcClient jdbc) {
        this.jpa = jpa;
        this.history = history;
        this.jdbc = jdbc;
    }

    /** Advisory lock da transação, por profissional: liberado no commit ou rollback. */
    @Override
    public void lockSchedule(String professionalId) {
        jdbc.sql("select pg_advisory_xact_lock(hashtext('agenda:' || :professionalId))")
                .param("professionalId", professionalId).query().singleRow();
    }

    @Override
    public List<Appointment> find(Filter f) {
        Specification<AppointmentEntity> spec = (root, query, cb) -> {
            List<Predicate> p = new ArrayList<>();
            if (f.status() != null) {
                p.add(cb.equal(root.get("status"), f.status().value()));
            }
            if (f.date() != null) {
                p.add(cb.equal(root.get("date"), f.date()));
            }
            if (f.from() != null) {
                p.add(cb.greaterThanOrEqualTo(root.get("date"), f.from()));
            }
            if (f.to() != null) {
                p.add(cb.lessThanOrEqualTo(root.get("date"), f.to()));
            }
            if (f.professionalId() != null) {
                p.add(cb.equal(root.get("professionalId"), f.professionalId()));
            }
            return cb.and(p.toArray(Predicate[]::new));
        };
        return jpa.findAll(spec, ORDER).stream().map(EntityMapper::toDomain).toList();
    }

    @Override
    public Optional<Appointment> findById(UUID id) {
        return jpa.findById(id).map(EntityMapper::toDomain);
    }

    @Override
    public Optional<Appointment> findByIdForUpdate(UUID id) {
        return jpa.findByIdForUpdate(id).map(EntityMapper::toDomain);
    }

    @Override
    public List<Appointment> findActive(Collection<String> professionalIds, LocalDate from, LocalDate to) {
        if (professionalIds.isEmpty()) {
            return List.of();
        }
        return jpa.findActive(professionalIds, from, to).stream().map(EntityMapper::toDomain).toList();
    }

    @Override
    public List<Appointment> findConfirmed(String professionalId, LocalDate from) {
        return jpa.findConfirmedFrom(professionalId, from).stream().map(EntityMapper::toDomain).toList();
    }

    @Override
    public Appointment save(Appointment appointment) {
        try {
            return EntityMapper.toDomain(jpa.saveAndFlush(EntityMapper.toEntity(appointment)));
        } catch (DataIntegrityViolationException e) {
            if (isExclusionViolation(e)) {
                throw new SlotTakenException();
            }
            throw e;
        }
    }

    @Override
    public void appendHistory(StatusChange c) {
        history.save(new AppointmentStatusHistoryEntity(c.appointmentId(), c.from().value(), c.to().value(), c.at(),
                c.actor(), c.reason()));
    }

    @Override
    public PendingCount pendingCount(String professionalId) {
        String pending = AppointmentStatus.PENDING.value();
        if (professionalId == null) {
            return new PendingCount(jpa.countByStatus(pending), jpa.countByStatusAndSeenAtIsNull(pending));
        }
        return new PendingCount(jpa.countByStatusAndProfessionalId(pending, professionalId),
                jpa.countByStatusAndProfessionalIdAndSeenAtIsNull(pending, professionalId));
    }

    static boolean isExclusionViolation(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && EXCLUSION_VIOLATION.equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }
}
