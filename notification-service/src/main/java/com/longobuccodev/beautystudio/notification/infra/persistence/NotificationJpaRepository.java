package com.longobuccodev.beautystudio.notification.infra.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface NotificationJpaRepository extends JpaRepository<NotificationEntity, UUID> {

    Optional<NotificationEntity> findByEventIdAndRecipientRole(UUID eventId, String recipientRole);
}
