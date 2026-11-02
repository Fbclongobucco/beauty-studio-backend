package com.longobuccodev.beautystudio.notification.core.repository;

import com.longobuccodev.beautystudio.notification.core.domain.Notification;
import com.longobuccodev.beautystudio.notification.core.domain.RecipientRole;

import java.util.Optional;
import java.util.UUID;

public interface NotificationRepository {

    Optional<Notification> find(UUID eventId, RecipientRole role);

    /** Grava um registro novo; vazio se já existe outro para o mesmo evento e destinatário. */
    Optional<Notification> insertIfAbsent(Notification notification);

    Notification update(Notification notification);
}
