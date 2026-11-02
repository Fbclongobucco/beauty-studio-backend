package com.longobuccodev.beautystudio.booking.infra;

import com.longobuccodev.beautystudio.booking.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 8.5 e 8.6: catálogo no painel, profissionais e o WhatsApp informado pela própria profissional. */
class CatalogAdminIT extends IntegrationTest {

    @Test
    void profissionalVeSoOsPropriosServicos() throws Exception {
        mvc.perform(get("/api/admin/services").cookie(michele())).andExpect(status().isOk())
                .andExpect(jsonPath("$.services.length()").value(1))
                .andExpect(jsonPath("$.services[0].name").value("Depilação"))
                .andExpect(jsonPath("$.professionals.length()").value(1))
                .andExpect(jsonPath("$.categories.length()").value(3));
        mvc.perform(get("/api/admin/services").cookie(master()))
                .andExpect(jsonPath("$.services.length()").value(21))
                .andExpect(jsonPath("$.services[15].durationEstimated").value(true))
                .andExpect(jsonPath("$.services[15].photoUrl").isEmpty());
    }

    @Test
    void edicaoDePrecoNaoMudaPedidoExistente() throws Exception {
        String id = book("manicure", "2026-11-06", "11:00");
        mvc.perform(json(patch("/api/admin/services/manicure").cookie(stefani()), "{\"priceCents\":2500}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.priceCents").value(2500));
        assertThat(mvc.perform(get("/api/services")).andReturn().getResponse().getContentAsString())
                .contains("\"id\":\"manicure\",\"name\":\"Manicure\",\"subcategory\":\"Cuidados básicos\",\"durationMin\":30,\"priceCents\":2500");
        mvc.perform(get("/api/appointments/" + id)).andExpect(jsonPath("$.priceCents").value(2000));
    }

    @Test
    void editarDuracaoRemoveAMarcaDeEstimativa() throws Exception {
        mvc.perform(json(patch("/api/admin/services/box-braids").cookie(lorrayne()), "{\"durationMin\":300}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMin").value(300))
                .andExpect(jsonPath("$.durationEstimated").value(false));
    }

    @Test
    void validacoesDeDuracaoEPreco() throws Exception {
        mvc.perform(json(patch("/api/admin/services/manicure").cookie(stefani()), "{\"durationMin\":45,\"priceCents\":-100}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.durationMin").value("A duração deve ser múltiplo de 30 minutos."))
                .andExpect(jsonPath("$.fields.priceCents").value("Informe um valor em reais, ex.: 45,00"));
    }

    @Test
    void profissionalCriaServicoSempreEmNomeDela() throws Exception {
        mvc.perform(json(post("/api/admin/services").cookie(stefani()), """
                        {"name":"Spa dos pés","categoryId":"unhas","subcategory":"Cuidados básicos",
                         "durationMin":60,"priceCents":4500}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("spa-dos-pes"))
                .andExpect(jsonPath("$.professionalId").value("stefani"))
                .andExpect(jsonPath("$.durationEstimated").value(false))
                .andExpect(jsonPath("$.sortOrder").value(22));
        assertThat(mvc.perform(get("/api/availability?serviceId=spa-dos-pes&date=2026-11-06"))
                .andReturn().getResponse().getStatus()).isEqualTo(200);
        mvc.perform(json(post("/api/admin/services").cookie(stefani()), """
                        {"name":"Outro","categoryId":"unhas","durationMin":60,"professionalId":"lorrayne"}"""))
                .andExpect(status().isForbidden());
    }

    @Test
    void servicoAlheioResponde404() throws Exception {
        mvc.perform(json(patch("/api/admin/services/box-braids").cookie(michele()), "{\"priceCents\":1}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Serviço não encontrado."));
    }

    @Test
    void masterEditaObservacaoDaCategoria() throws Exception {
        mvc.perform(json(patch("/api/admin/categories/unhas").cookie(master()), "{\"note\":\"Encapsular: + R$ 20,00.\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.note").value("Encapsular: + R$ 20,00."));
        assertThat(mvc.perform(get("/api/services")).andReturn().getResponse().getContentAsString())
                .contains("Encapsular: + R$ 20,00.");
        mvc.perform(json(post("/api/admin/categories").cookie(master()), "{\"name\":\"Cílios\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("cilios"))
                .andExpect(jsonPath("$.sortOrder").value(4));
    }

    // ---- WhatsApp da profissional (8.6) ----

    @Test
    void profissionalCadastraOProprioNumero() throws Exception {
        Cookie stefani = stefani();
        mvc.perform(json(patch("/api/admin/me").cookie(stefani), "{\"whatsappPhone\":\"(21) 99999-0000\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("professional"))
                .andExpect(jsonPath("$.whatsappPhone").value("+5521999990000"));
        mvc.perform(get("/api/admin/me").cookie(stefani)).andExpect(jsonPath("$.whatsappPhone").value("+5521999990000"));
        mvc.perform(get("/api/admin/professionals").cookie(master()))
                .andExpect(jsonPath("$.professionals[0].whatsappPhone").value("+5521999990000"));

        // Próximo pedido da Stéfani leva o número novo no evento
        book("manicure", "2026-11-06", "11:00");
        String payload = jdbc.sql("select payload::text from outbox_event where type = 'appointment.created'")
                .query(String.class).single();
        assertThat(payload).contains("\"whatsappPhone\": \"+5521999990000\"");
    }

    @Test
    void profissionalRemoveONumero() throws Exception {
        Cookie lorrayne = lorrayne();
        mvc.perform(json(patch("/api/admin/me").cookie(lorrayne), "{\"whatsappPhone\":\"(21) 98888-1111\"}"));
        mvc.perform(json(patch("/api/admin/me").cookie(lorrayne), "{\"whatsappPhone\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.whatsappPhone").isEmpty());
        book("box-braids", "2026-11-06", "09:00");
        assertThat(jdbc.sql("select payload::text from outbox_event").query(String.class).single())
                .contains("\"whatsappPhone\": null");
    }

    @Test
    void masterNaoAlteraNumeroDeProfissional() throws Exception {
        Cookie master = master();
        mvc.perform(json(patch("/api/admin/professionals/stefani").cookie(master), "{\"whatsappPhone\":\"(21) 99999-0000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.whatsappPhone").value("Só a profissional altera o próprio WhatsApp, no painel dela."));
        mvc.perform(json(patch("/api/admin/me").cookie(master), "{\"whatsappPhone\":\"(21) 99999-0000\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(jdbc.sql("select count(*) from professional where whatsapp_phone is not null").query(Long.class)
                .single()).isZero();
    }

    @Test
    void numeroInvalido() throws Exception {
        mvc.perform(json(patch("/api/admin/me").cookie(michele()), "{\"whatsappPhone\":\"9999-0000\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.whatsappPhone").value("Informe o WhatsApp com DDD, ex.: (21) 96811-7976"));
    }

    @Test
    void whatsappDaProfissionalNuncaNosEndpointsPublicos() throws Exception {
        mvc.perform(json(patch("/api/admin/me").cookie(stefani()), "{\"whatsappPhone\":\"(21) 99999-0000\"}"));
        String id = book("manicure", "2026-11-06", "11:00");
        assertThat(mvc.perform(get("/api/services")).andReturn().getResponse().getContentAsString())
                .doesNotContain("99999");
        assertThat(mvc.perform(get("/api/appointments/" + id)).andReturn().getResponse().getContentAsString())
                .doesNotContain("99999").doesNotContain("98888");
    }

    @Test
    void masterEditaNomeEAtivoDaProfissional() throws Exception {
        mvc.perform(json(patch("/api/admin/professionals/michele").cookie(master()), "{\"name\":\"Michele B.\",\"active\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.name").value("Michele B."));
        mvc.perform(json(patch("/api/admin/professionals/michele").cookie(michele()), "{\"name\":\"X\"}"))
                .andExpect(status().isForbidden());
    }
}
