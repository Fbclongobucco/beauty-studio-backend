package com.longobuccodev.beautystudio.messaging.core;

import com.longobuccodev.beautystudio.messaging.infra.AppointmentEventCodec;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppointmentEventJsonTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final AppointmentEventCodec codec = new AppointmentEventCodec(mapper);

    static AppointmentEvent sample() {
        return AppointmentEvent.of(AppointmentEventType.CREATED,
                UUID.fromString("7c1e0b5e-8f7a-4c55-9a4e-0d6f3c1b2a10"),
                OffsetDateTime.parse("2026-11-02T10:15:30-03:00"),
                new AppointmentEvent.Appointment("a1", "pending", LocalDate.parse("2026-11-06"), "11:00",
                        OffsetDateTime.parse("2026-11-06T11:00:00-03:00"),
                        OffsetDateTime.parse("2026-11-06T11:30:00-03:00"), 2000, 30, "Unha curta", null),
                new AppointmentEvent.Service("manicure", "Manicure"),
                new AppointmentEvent.Professional("stefani", "Stéfani Longo Bucco", "+5521999990000"),
                new AppointmentEvent.Customer("Maria", "+5521988887777"),
                "https://salao.exemplo/agendamento/a1");
    }

    @Test
    void idaEVoltaPreservaOEvento() {
        AppointmentEvent event = sample();
        AppointmentEvent back = codec.decode(codec.encode(event));
        assertThat(back).isEqualTo(event);
    }

    @Test
    void jsonUsaOsNomesEFormatosDoContrato() {
        JsonNode json = mapper.readTree(new String(codec.encode(sample()), StandardCharsets.UTF_8));
        assertThat(json.get("type").asString()).isEqualTo("appointment.created");
        assertThat(json.get("version").asInt()).isEqualTo(EventContract.CONTRACT_VERSION);
        assertThat(json.get("appointment").get("date").asString()).isEqualTo("2026-11-06");
        assertThat(json.get("appointment").get("start").asString()).isEqualTo("2026-11-06T11:00:00-03:00");
        assertThat(json.get("professional").get("whatsappPhone").asString()).isEqualTo("+5521999990000");
        assertThat(json.get("customer").get("phone").asString()).isEqualTo("+5521988887777");
    }

    @Test
    void camposDesconhecidosNaoQuebramOConsumidor() {
        String json = new String(codec.encode(sample()), StandardCharsets.UTF_8).replaceFirst("\\{", "{\"novo\":1,");
        assertThat(codec.decode(json.getBytes(StandardCharsets.UTF_8)).eventId()).isEqualTo(sample().eventId());
    }

    @Test
    void eventoSemEventIdEInvalido() {
        String json = "{\"type\":\"appointment.created\",\"version\":1}";
        AppointmentEvent event = codec.decode(json.getBytes(StandardCharsets.UTF_8));
        assertThatThrownBy(event::requireValid).isInstanceOf(InvalidEventException.class).hasMessageContaining("eventId");
    }

    @Test
    void jsonQuebradoEInvalido() {
        assertThatThrownBy(() -> codec.decode("{nao e json".getBytes(StandardCharsets.UTF_8)))
                .isInstanceOf(InvalidEventException.class);
    }

    @Test
    void tipoDesconhecidoEInvalido() {
        AppointmentEvent e = sample();
        AppointmentEvent other = new AppointmentEvent(e.eventId(), "appointment.unknown", 1, e.occurredAt(),
                e.appointment(), e.service(), e.professional(), e.customer(), e.statusUrl());
        assertThatThrownBy(other::requireValid).isInstanceOf(InvalidEventException.class);
    }

    @Test
    void routingKeysDosTipos() {
        assertThat(AppointmentEventType.fromRoutingKey("appointment.cancelled")).contains(AppointmentEventType.CANCELLED);
        assertThat(AppointmentEventType.fromRoutingKey("x")).isEmpty();
    }
}
