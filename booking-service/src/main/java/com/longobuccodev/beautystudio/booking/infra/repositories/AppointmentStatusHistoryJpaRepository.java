package com.longobuccodev.beautystudio.booking.infra.repositories;

import com.longobuccodev.beautystudio.booking.infra.entities.AppointmentStatusHistoryEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AppointmentStatusHistoryJpaRepository extends JpaRepository<AppointmentStatusHistoryEntity, Long> {

    List<AppointmentStatusHistoryEntity> findByAppointmentIdOrderByIdAsc(UUID appointmentId);
}
