package com.longobuccodev.beautystudio.notification.core.domain;

import java.time.LocalDate;

/**
 * Dados do agendamento para montar a mensagem, independentes do formato do evento.
 *
 * @param status     "confirmed", "rejected" ou "cancelled" (avisos à cliente); "pending" no pedido novo
 * @param priceCents nulo = "Consultar valor"
 */
public record AppointmentNotice(String customerName, String customerPhone, String professionalName,
                                String professionalPhone, String serviceName, LocalDate date, String time,
                                Integer priceCents, String notes, String reason, String status, String statusUrl) {
}
