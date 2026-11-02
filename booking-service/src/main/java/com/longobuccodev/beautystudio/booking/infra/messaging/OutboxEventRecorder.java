package com.longobuccodev.beautystudio.booking.infra.messaging;

import com.longobuccodev.beautystudio.booking.core.domain.Appointment;
import com.longobuccodev.beautystudio.booking.core.domain.Professional;
import com.longobuccodev.beautystudio.booking.core.domain.Schedule;
import com.longobuccodev.beautystudio.booking.core.event.AppointmentEventKind;
import com.longobuccodev.beautystudio.booking.core.event.EventRecorder;
import com.longobuccodev.beautystudio.booking.infra.config.BookingProperties;
import com.longobuccodev.beautystudio.messaging.core.AppointmentEvent;
import com.longobuccodev.beautystudio.messaging.core.AppointmentEventType;
import com.longobuccodev.beautystudio.messaging.infra.AppointmentEventCodec;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Grava o evento na tabela outbox, na transação corrente (a mesma da mudança do agendamento).
 * O WhatsApp da profissional vai como estava no momento do evento.
 */
@Component
public class OutboxEventRecorder implements EventRecorder {

    private final JdbcClient jdbc;
    private final AppointmentEventCodec codec;
    private final Clock clock;
    private final String frontUrl;

    public OutboxEventRecorder(JdbcClient jdbc, AppointmentEventCodec codec, Clock clock,
                               BookingProperties properties) {
        this.jdbc = jdbc;
        this.codec = codec;
        this.clock = clock;
        this.frontUrl = trimSlash(properties.publicUrls().frontUrl());
    }

    @Override
    public void record(AppointmentEventKind kind, Appointment a, Professional professional) {
        AppointmentEvent event = toEvent(kind, a, professional);
        jdbc.sql("""
                        insert into outbox_event (id, aggregate_id, type, payload, created_at)
                        values (:id, :aggregate, :type, cast(:payload as jsonb), :createdAt)""")
                .param("id", event.eventId())
                .param("aggregate", a.id().toString())
                .param("type", event.type())
                .param("payload", new String(codec.encode(event), StandardCharsets.UTF_8))
                .param("createdAt", Timestamp.from(clock.instant()))
                .update();
    }

    AppointmentEvent toEvent(AppointmentEventKind kind, Appointment a, Professional p) {
        return AppointmentEvent.of(AppointmentEventType.valueOf(kind.name()), UUID.randomUUID(),
                OffsetDateTime.ofInstant(clock.instant(), Schedule.ZONE),
                new AppointmentEvent.Appointment(a.id().toString(), a.status().value(), a.date(),
                        Schedule.formatTime(a.time()), a.start(), a.end(), a.priceCents(), a.durationMin(), a.notes(),
                        a.reason()),
                new AppointmentEvent.Service(a.serviceId(), a.serviceName()),
                new AppointmentEvent.Professional(a.professionalId(),
                        p == null ? a.professionalName() : p.name(), p == null ? null : p.whatsappPhone()),
                new AppointmentEvent.Customer(a.customerName(), a.customerPhoneE164()),
                frontUrl + "/agendamento/" + a.id());
    }

    static String trimSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
