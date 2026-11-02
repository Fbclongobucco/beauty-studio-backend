package com.longobuccodev.beautystudio.notification.core.domain;

import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

/**
 * Textos em pt-BR das mensagens de WhatsApp. Curtos e sem abreviações, pensados para clientes idosas.
 * Datas por extenso ("sexta-feira, 6 de novembro") e valores como no front ("R$ 20,00").
 */
public final class MessageTemplate {

    public static final String SALON_NAME = "Longo Bucco Beauty Studio";
    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final DateTimeFormatter DATE_LONG = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", PT_BR);
    private static final Map<String, String> STATUS_LABEL = Map.of(
            "confirmed", "Confirmado", "rejected", "Recusado", "cancelled", "Cancelado");

    private MessageTemplate() {
    }

    public static String dateLong(LocalDate date) {
        return date.format(DATE_LONG);
    }

    /** "R$ 1.234,56" com espaço comum, ou "Consultar valor". */
    public static String price(Integer cents) {
        if (cents == null) {
            return "Consultar valor";
        }
        return NumberFormat.getCurrencyInstance(PT_BR).format(cents / 100.0).replace(' ', ' ');
    }

    public static String statusLabel(String status) {
        return STATUS_LABEL.getOrDefault(status, status);
    }

    /** Aviso de pedido novo para a profissional, com o link do painel para confirmar. */
    public static String newRequestForProfessional(AppointmentNotice n, String panelUrl) {
        StringBuilder text = new StringBuilder()
                .append("Novo pedido de agendamento - ").append(SALON_NAME).append("\n\n")
                .append("Cliente: ").append(n.customerName()).append('\n')
                .append("Serviço: ").append(n.serviceName()).append('\n')
                .append("Data: ").append(dateLong(n.date())).append('\n')
                .append("Horário: ").append(n.time()).append('\n')
                .append("Valor: ").append(price(n.priceCents())).append('\n');
        if (n.notes() != null && !n.notes().isBlank()) {
            text.append("Observação da cliente: ").append(n.notes()).append('\n');
        }
        return text.append('\n').append("Confirme ou recuse no painel: ").append(panelUrl).toString();
    }

    /**
     * Aviso de status para a cliente. Recusado ou cancelado traz o WhatsApp do salão, quando configurado.
     */
    public static String statusForCustomer(AppointmentNotice n, String salonWhatsapp) {
        StringBuilder text = new StringBuilder()
                .append(SALON_NAME).append("\n\n")
                .append("Olá, ").append(firstName(n.customerName())).append(".\n")
                .append("Situação do seu agendamento: ").append(statusLabel(n.status())).append("\n\n")
                .append("Serviço: ").append(n.serviceName()).append('\n')
                .append("Data: ").append(dateLong(n.date())).append('\n')
                .append("Horário: ").append(n.time()).append('\n');
        if (n.reason() != null && !n.reason().isBlank()) {
            text.append("Motivo: ").append(n.reason()).append('\n');
        }
        text.append('\n').append("Acompanhe seu pedido: ").append(n.statusUrl());
        boolean notConfirmed = "rejected".equals(n.status()) || "cancelled".equals(n.status());
        if (notConfirmed && salonWhatsapp != null && !salonWhatsapp.isBlank()) {
            text.append('\n').append("Fale com o salão pelo WhatsApp: ").append(Phones.format(salonWhatsapp));
        }
        return text.toString();
    }

    private static String firstName(String name) {
        return name == null || name.isBlank() ? "" : name.strip().split("\\s+")[0];
    }
}
