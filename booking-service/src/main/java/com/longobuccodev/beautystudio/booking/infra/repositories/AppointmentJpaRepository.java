package com.longobuccodev.beautystudio.booking.infra.repositories;

import com.longobuccodev.beautystudio.booking.infra.entities.AppointmentEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentJpaRepository extends JpaRepository<AppointmentEntity, UUID>,
        JpaSpecificationExecutor<AppointmentEntity> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from AppointmentEntity a where a.id = :id")
    Optional<AppointmentEntity> findByIdForUpdate(@Param("id") UUID id);

    @Query("""
            select a from AppointmentEntity a
            where a.professionalId in :professionalIds and a.status in ('pending', 'confirmed')
              and a.date between :from and :to""")
    List<AppointmentEntity> findActive(@Param("professionalIds") Collection<String> professionalIds,
                                       @Param("from") LocalDate from, @Param("to") LocalDate to);

    @Query("""
            select a from AppointmentEntity a
            where a.professionalId = :professionalId and a.status = 'confirmed' and a.date >= :from""")
    List<AppointmentEntity> findConfirmedFrom(@Param("professionalId") String professionalId,
                                              @Param("from") LocalDate from);

    long countByStatus(String status);

    long countByStatusAndSeenAtIsNull(String status);

    long countByStatusAndProfessionalId(String status, String professionalId);

    long countByStatusAndProfessionalIdAndSeenAtIsNull(String status, String professionalId);
}
