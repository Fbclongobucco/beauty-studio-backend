package com.longobuccodev.beautystudio.booking.infra;

import com.longobuccodev.beautystudio.booking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 8.1 e 8.2: catálogo, disponibilidade e pedido, com o mesmo JSON de beauty-studio/lib/domain/types.ts. */
class PublicApiIT extends IntegrationTest {

    static final JsonMapper MAPPER = JsonMapper.builder().build();

    static List<String> keys(JsonNode node) {
        List<String> out = new ArrayList<>(node.propertyNames());
        return out;
    }

    JsonNode getJson(String url) throws Exception {
        return MAPPER.readTree(mvc.perform(get(url)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString());
    }

    @Test
    void catalogoPublicoNoFormatoDoFrontSemWhatsappNemEstimativa() throws Exception {
        JsonNode body = getJson("/api/services");
        assertThat(keys(body)).containsExactly("categories");
        assertThat(body.get("categories")).extracting(c -> c.get("name").asString())
                .containsExactly("Unhas", "Tranças", "Depilação");
        JsonNode trancas = body.get("categories").get(1);
        assertThat(keys(trancas)).containsExactly("id", "name", "note", "services");
        assertThat(trancas.get("note").asString()).isEqualTo("Valores somente de mão de obra.");
        JsonNode box = trancas.get("services").get(3);
        assertThat(keys(box)).containsExactly("id", "name", "subcategory", "durationMin", "priceCents",
                "professional", "photoUrl");
        assertThat(box.get("name").asString()).isEqualTo("Box braids");
        assertThat(box.get("priceCents").asInt()).isEqualTo(19000);
        assertThat(box.get("professional").get("name").asString()).isEqualTo("Lorrayne Longobuco");
        assertThat(box.get("photoUrl").isNull()).isTrue();
        String raw = body.toString();
        assertThat(raw).doesNotContain("whatsapp").doesNotContain("durationEstimated");
        int total = 0;
        for (JsonNode c : body.get("categories")) {
            total += c.get("services").size();
        }
        assertThat(total).isEqualTo(21);
    }

    @Test
    void servicoInativoSomeECategoriaSemServicoAtivoNaoAparece() throws Exception {
        jdbc.sql("update service set active = false where category_id = 'depilacao'").update();
        assertThat(getJson("/api/services").get("categories")).extracting(c -> c.get("id").asString())
                .containsExactly("unhas", "trancas");
    }

    @Test
    void disponibilidadeRespeitaGradeDuracaoEConfirmados() throws Exception {
        JsonNode nago = getJson("/api/availability?serviceId=nago-7-12&date=2026-11-03");
        assertThat(keys(nago)).containsExactly("serviceId", "date", "slots", "taken");
        assertThat(nago.get("taken").size()).isZero();
        assertThat(nago.get("slots").get(nago.get("slots").size() - 1).asString()).isEqualTo("18:00");

        String id = book("manicure-pedicure", "2026-11-06", "10:00");
        mvc.perform(json(post("/api/admin/appointments/" + id + "/confirm").cookie(stefani()), "{}"))
                .andExpect(status().isOk());
        JsonNode manicure = getJson("/api/availability?serviceId=manicure&date=2026-11-06");
        List<String> slots = new ArrayList<>();
        manicure.get("slots").forEach(s -> slots.add(s.asString()));
        assertThat(slots).doesNotContain("10:00", "10:30").contains("09:30", "11:00");
        // show-taken-slots: o horário ocupado vem em "taken", sem dizer quem ocupa
        List<String> taken = new ArrayList<>();
        manicure.get("taken").forEach(s -> taken.add(s.asString()));
        assertThat(taken).containsExactly("10:00", "10:30");
        assertThat(manicure.toString()).doesNotContain("customer", "Stéfani");
        assertThat(getJson("/api/availability?serviceId=depilacao&date=2026-11-06").toString()).contains("\"10:00\"");
    }

    @Test
    void pendenteNaoBloqueia() throws Exception {
        book("manicure", "2026-11-06", "11:00");
        JsonNode manicure = getJson("/api/availability?serviceId=manicure&date=2026-11-06");
        assertThat(manicure.get("slots").toString()).contains("\"11:00\"");
        assertThat(manicure.get("taken").size()).isZero();
    }

    @Test
    void diasComDomingoFechadoEJanelaDe30Dias() throws Exception {
        JsonNode days = getJson("/api/availability/days?serviceId=manicure&from=2026-11-01&days=8");
        assertThat(keys(days)).containsExactly("serviceId", "days");
        // Hoje é 02/11: 01/11 já passou e fica fora da janela
        assertThat(days.get("days").size()).isEqualTo(7);
        assertThat(days.get("days").get(0).toString())
                .isEqualTo("{\"date\":\"2026-11-02\",\"open\":true,\"hasSlots\":true}");
        assertThat(days.get("days").get(6).toString())
                .isEqualTo("{\"date\":\"2026-11-08\",\"open\":false,\"hasSlots\":false}");
        assertThat(getJson("/api/availability/days?serviceId=manicure&from=2026-11-02").get("days").size()).isEqualTo(31);
        mvc.perform(get("/api/availability/days?serviceId=manicure&from=2026-11-02&days=0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fields.days").value("Informe de 1 a 62 dias."));
        mvc.perform(get("/api/availability?serviceId=nao-existe&date=2026-11-06")).andExpect(status().isNotFound());
    }

    @Test
    void pedidoValidoResponde201NoFormatoDoFrontEGravaOEvento() throws Exception {
        String res = mvc.perform(json(post("/api/appointments"), """
                        {"serviceId":"depilacao","date":"2026-11-06","time":"15:00","customerName":"  Maria   da Silva ",
                         "customerPhone":"(21) 98888-7777","notes":"Primeira vez"}"""))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        JsonNode a = MAPPER.readTree(res);
        assertThat(keys(a)).containsExactly("id", "serviceId", "professionalId", "serviceName", "professionalName",
                "durationMin", "priceCents", "date", "time", "startISO", "endISO", "customerName",
                "customerPhoneE164", "notes", "status", "createdAt", "confirmedAt", "rejectedAt", "cancelledAt",
                "reason", "seenByAdminAt");
        assertThat(a.get("status").asString()).isEqualTo("pending");
        assertThat(a.get("priceCents").asInt()).isEqualTo(8000);
        assertThat(a.get("durationMin").asInt()).isEqualTo(60);
        assertThat(a.get("startISO").asString()).isEqualTo("2026-11-06T15:00:00-03:00");
        assertThat(a.get("endISO").asString()).isEqualTo("2026-11-06T16:00:00-03:00");
        assertThat(a.get("customerName").asString()).isEqualTo("Maria da Silva");
        assertThat(a.get("customerPhoneE164").asString()).isEqualTo("+5521988887777");
        assertThat(outboxCount("appointment.created")).isEqualTo(1);
    }

    @Test
    void dadosInvalidosResponde400ComOsCamposESemEvento() throws Exception {
        mvc.perform(json(post("/api/appointments"),
                        "{\"serviceId\":\"manicure\",\"date\":\"06/11/2026\",\"time\":\"11:00\",\"customerName\":\"Maria\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.customerPhone").value("Informe o WhatsApp com DDD, ex.: (21) 96811-7976"))
                .andExpect(jsonPath("$.fields.date").value("Data inválida (use AAAA-MM-DD)."));
        mvc.perform(json(post("/api/appointments"), """
                        {"serviceId":"manicure","date":"2026-11-06","time":"11:00","customerName":"Maria",
                         "customerPhone":"9681-7976"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.customerPhone").value("Informe o WhatsApp com DDD, ex.: (21) 96811-7976"));
        assertThat(outboxCount("appointment.created")).isZero();
    }

    @Test
    void horarioOcupadoOuForaDoExpedienteResponde409SemEvento() throws Exception {
        String id = book("manicure", "2026-11-06", "11:00");
        mvc.perform(json(post("/api/admin/appointments/" + id + "/confirm").cookie(stefani()), "{}"));
        long before = outboxCount("appointment.created");
        mvc.perform(json(post("/api/appointments"), """
                        {"serviceId":"manicure","date":"2026-11-06","time":"11:00","customerName":"Ana",
                         "customerPhone":"(21) 98888-6666"}"""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Esse horário acabou de ser ocupado. Escolha outro."));
        mvc.perform(json(post("/api/appointments"), """
                        {"serviceId":"manicure","date":"2026-11-08","time":"11:00","customerName":"Ana",
                         "customerPhone":"(21) 98888-6666"}"""))
                .andExpect(status().isConflict());
        assertThat(outboxCount("appointment.created")).isEqualTo(before);
    }

    @Test
    void statusPublicoSemDadosDeContato() throws Exception {
        String id = book("manicure", "2026-11-06", "11:00");
        JsonNode p = getJson("/api/appointments/" + id);
        assertThat(keys(p)).containsExactly("id", "status", "serviceName", "professionalName", "durationMin",
                "priceCents", "date", "time", "startISO", "endISO", "customerFirstName");
        assertThat(p.get("customerFirstName").asString()).isEqualTo("Maria");
        mvc.perform(get("/api/appointments/nao-e-uuid")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Agendamento não encontrado."));
    }

    @Test
    void servidorEmUtcAindaOferece19e30EmSaoPaulo() throws Exception {
        clock.set(java.time.Instant.parse("2026-11-02T22:29:00Z"));
        assertThat(getJson("/api/availability?serviceId=manicure&date=2026-11-02").get("slots").toString())
                .isEqualTo("[\"19:30\"]");
    }
}
