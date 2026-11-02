package com.longobuccodev.beautystudio.messaging.core;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Evento de agendamento (contrato versao {@link EventContract#CONTRACT_VERSION}).
 * Carrega todos os dados para notificar, sem consulta ao booking-service (transferencia de estado por evento).
 *
 * @param eventId    identificador unico, base da idempotencia dos consumidores
 * @param type       routing key do evento, ex.: {@code appointment.created}
 * @param version    versao do contrato
 * @param occurredAt instante da mudanca, ISO 8601
 * @param statusUrl  link publico do pedido para a cliente
 */
public record AppointmentEvent(
        UUID eventId,
        String type,
        int version,
        OffsetDateTime occurredAt,
        Appointment appointment,
        Service service,
        Professional professional,
        Customer customer,
        String statusUrl) {

    /**
     * @param time       HH:mm no fuso do salao
     * @param start      inicio com deslocamento -03:00
     * @param end        fim com deslocamento -03:00
     * @param priceCents preco gravado no pedido; nulo = "Consultar valor"
     * @param reason     motivo informado na recusa ou no cancelamento
     */
    public record Appointment(
            String id,
            String status,
            LocalDate date,
            String time,
            OffsetDateTime start,
            OffsetDateTime end,
            Integer priceCents,
            int durationMin,
            String notes,
            String reason) {
    }

    public record Service(String id, String name) {
    }

    /** @param whatsappPhone E.164 configurado pela propria profissional; nulo quando ela nao informou */
    public record Professional(String id, String name, String whatsappPhone) {
    }

    /** @param phone E.164 informado pela cliente ao agendar */
    public record Customer(String name, String phone) {
    }

    public static AppointmentEvent of(AppointmentEventType type, UUID eventId, OffsetDateTime occurredAt,
                                      Appointment appointment, Service service, Professional professional,
                                      Customer customer, String statusUrl) {
        return new AppointmentEvent(eventId, type.routingKey(), EventContract.CONTRACT_VERSION, occurredAt,
                appointment, service, professional, customer, statusUrl);
    }

    /** Tipo do evento; {@link InvalidEventException} se desconhecido. */
    public AppointmentEventType eventType() {
        return AppointmentEventType.fromRoutingKey(type)
                .orElseThrow(() -> new InvalidEventException("Tipo de evento desconhecido: " + type));
    }

    /** Confere os campos obrigatorios do contrato. Usado pelo consumidor antes de processar. */
    public AppointmentEvent requireValid() {
        require(eventId != null, "eventId");
        eventType();
        require(occurredAt != null, "occurredAt");
        require(appointment != null && appointment.id() != null && appointment.date() != null
                && appointment.time() != null, "appointment");
        require(service != null && service.name() != null, "service");
        require(professional != null && professional.id() != null, "professional");
        require(customer != null && customer.phone() != null, "customer");
        return this;
    }

    private static void require(boolean ok, String field) {
        if (!ok) {
            throw new InvalidEventException("Evento sem o campo obrigatorio: " + field);
        }
    }
}
