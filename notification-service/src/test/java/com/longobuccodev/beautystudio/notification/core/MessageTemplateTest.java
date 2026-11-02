package com.longobuccodev.beautystudio.notification.core;

import com.longobuccodev.beautystudio.notification.core.domain.AppointmentNotice;
import com.longobuccodev.beautystudio.notification.core.domain.MessageTemplate;
import com.longobuccodev.beautystudio.notification.core.domain.Phones;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/** Textos dos cenários da spec whatsapp-notification. */
class MessageTemplateTest {

    static AppointmentNotice notice(String status, String reason, Integer price, String notes) {
        return new AppointmentNotice("Maria da Silva", "+5521988887777", "Stéfani Longo Bucco", "+5521999990000",
                "Manicure", LocalDate.parse("2026-11-06"), "11:00", price, notes, reason, status,
                "https://salao.exemplo/agendamento/a1");
    }

    @Test
    void pedidoNovoParaAProfissional() {
        String text = MessageTemplate.newRequestForProfessional(notice("pending", null, 2000, "Unha curta"),
                "https://salao.exemplo/admin");
        assertThat(text).isEqualTo("""
                Novo pedido de agendamento - Longo Bucco Beauty Studio

                Cliente: Maria da Silva
                Serviço: Manicure
                Data: sexta-feira, 6 de novembro
                Horário: 11:00
                Valor: R$ 20,00
                Observação da cliente: Unha curta

                Confirme ou recuse no painel: https://salao.exemplo/admin""");
    }

    @Test
    void servicoSemPrecoESemObservacao() {
        String text = MessageTemplate.newRequestForProfessional(notice("pending", null, null, null), "x");
        assertThat(text).contains("Valor: Consultar valor").doesNotContain("Observação");
        assertThat(MessageTemplate.price(123456)).isEqualTo("R$ 1.234,56");
    }

    @Test
    void confirmacaoParaACliente() {
        String text = MessageTemplate.statusForCustomer(notice("confirmed", null, 2000, null), "+5521968117976");
        assertThat(text).isEqualTo("""
                Longo Bucco Beauty Studio

                Olá, Maria.
                Situação do seu agendamento: Confirmado

                Serviço: Manicure
                Data: sexta-feira, 6 de novembro
                Horário: 11:00

                Acompanhe seu pedido: https://salao.exemplo/agendamento/a1""");
    }

    @Test
    void cancelamentoComMotivoEContatoDoSalao() {
        String text = MessageTemplate.statusForCustomer(notice("cancelled", "Imprevisto de saúde", 2000, null),
                "+5521968117976");
        assertThat(text).contains("Situação do seu agendamento: Cancelado")
                .contains("Motivo: Imprevisto de saúde")
                .endsWith("Fale com o salão pelo WhatsApp: (21) 96811-7976");
        assertThat(MessageTemplate.statusForCustomer(notice("rejected", null, 2000, null), "+5521968117976"))
                .contains("Recusado").contains("(21) 96811-7976");
    }

    @Test
    void semWhatsappDoSalaoConfiguradoNaoInventaContato() {
        assertThat(MessageTemplate.statusForCustomer(notice("cancelled", null, 2000, null), ""))
                .doesNotContain("Fale com o salão");
    }

    @Test
    void mascaraDeTelefoneENome() {
        assertThat(Phones.mask("+5521968117976")).isEqualTo("+55219****7976");
        assertThat(Phones.maskName("Maria da Silva")).isEqualTo("M***");
        assertThat(Phones.digits("+5521968117976")).isEqualTo("5521968117976");
    }
}
