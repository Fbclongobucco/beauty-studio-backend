package com.longobuccodev.beautystudio.messaging.application;

import com.longobuccodev.beautystudio.messaging.core.AppointmentEvent;

/** Porta de publicacao. Retorna so depois da confirmacao do broker; falha lanca {@link PublishFailedException}. */
public interface EventPublisher {

    void publish(AppointmentEvent event);
}
