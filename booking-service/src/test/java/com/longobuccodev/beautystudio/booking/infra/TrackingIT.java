package com.longobuccodev.beautystudio.booking.infra;

import com.longobuccodev.beautystudio.booking.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** add-customer-request-tracking: cookie my_requests e GET /api/appointments/mine. */
class TrackingIT extends IntegrationTest {

    static final JsonMapper MAPPER = JsonMapper.builder().build();

    /** Pedido criado e o cookie my_requests devolvido na resposta. */
    record Created(String id, Cookie cookie) {
    }

    /** Cria o pedido levando o cookie atual. */
    private Created create(String serviceId, String date, String time, Cookie current) throws Exception {
        var req = json(post("/api/appointments"), """
                {"serviceId":"%s","date":"%s","time":"%s","customerName":"Maria da Silva",
                 "customerPhone":"(21) 98888-7777"}""".formatted(serviceId, date, time));
        if (current != null) {
            req.cookie(current);
        }
        MvcResult r = mvc.perform(req).andExpect(status().isCreated()).andReturn();
        return new Created(MAPPER.readTree(r.getResponse().getContentAsString()).get("id").asString(),
                r.getResponse().getCookie("my_requests"));
    }

    private JsonNode mine(Cookie cookie) throws Exception {
        var req = get("/api/appointments/mine");
        if (cookie != null) {
            req.cookie(cookie);
        }
        return MAPPER.readTree(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString());
    }

    private static List<String> ids(JsonNode body) {
        List<String> out = new ArrayList<>();
        body.get("appointments").forEach(a -> out.add(a.get("id").asString()));
        return out;
    }

    @Test
    void criarGravaOCookieEMineTrazOPendente() throws Exception {
        Created a = create("manicure", "2026-11-06", "11:00", null);
        Cookie c = a.cookie();
        assertThat(c).isNotNull();
        assertThat(c.getPath()).isEqualTo("/api/appointments");
        assertThat(c.isHttpOnly()).isTrue();
        assertThat(c.getValue()).doesNotContain("Maria");
        JsonNode body = mine(c);
        assertThat(ids(body)).containsExactly(a.id());
        JsonNode first = body.get("appointments").get(0);
        assertThat(first.get("status").asString()).isEqualTo("pending");
        assertThat(first.get("customerFirstName").asString()).isEqualTo("Maria");
        assertThat(first.has("customerPhoneE164")).isFalse();
    }

    @Test
    void abrirOLinkDoPedidoGravaOCookieNesteAparelho() throws Exception {
        String id = book("manicure", "2026-11-06", "11:00");
        Cookie c = mvc.perform(get("/api/appointments/" + id)).andExpect(status().isOk()).andReturn().getResponse()
                .getCookie("my_requests");
        assertThat(ids(mine(c))).containsExactly(id);
    }

    @Test
    void filtraEmAndamentoEOrdenaPeloInicio() throws Exception {
        Created tarde = create("manicure", "2026-11-06", "15:00", null);
        Created cedo = create("pedicure", "2026-11-03", "10:00", tarde.cookie());
        Created recusado = create("depilacao", "2026-11-04", "14:00", cedo.cookie());
        Created passado = create("manicure", "2026-11-02", "12:00", recusado.cookie());
        Cookie c = passado.cookie();
        Cookie m = master();
        mvc.perform(json(post("/api/admin/appointments/" + recusado.id() + "/reject").cookie(m), "{}"))
                .andExpect(status().isOk());
        mvc.perform(json(post("/api/admin/appointments/" + passado.id() + "/confirm").cookie(m), "{}"))
                .andExpect(status().isOk());

        // Recusado futuro continua aparecendo, para a cliente ver o desfecho
        assertThat(ids(mine(c))).containsExactly(passado.id(), cedo.id(), recusado.id(), tarde.id());

        // Quarta 15:00 em São Paulo: o confirmado de segunda terminou e o recusado de quarta 14:00 já começou,
        // então os dois saem; o pendente de terça continua (pendente sempre aparece)
        clock.set(java.time.Instant.parse("2026-11-04T18:00:00Z"));
        assertThat(ids(mine(c))).containsExactly(cedo.id(), tarde.id());
    }

    @Test
    void cookieAdulteradoContaComoVazio() throws Exception {
        Created a = create("manicure", "2026-11-06", "11:00", null);
        String v = (a.cookie()).getValue();
        Cookie tampered = new Cookie("my_requests", v.substring(0, v.length() - 3) + "abc");
        assertThat(ids(mine(tampered))).isEmpty();
        assertThat(ids(mine(new Cookie("my_requests", "lixo")))).isEmpty();
    }

    @Test
    void semCookieListaVaziaENaoGravaCookie() throws Exception {
        MvcResult r = mvc.perform(get("/api/appointments/mine")).andExpect(status().isOk()).andReturn();
        assertThat(r.getResponse().getContentAsString()).isEqualTo("{\"appointments\":[]}");
        assertThat(r.getResponse().getCookie("my_requests")).isNull();
    }

    @Test
    void limiteDe10PedidosNoCookie() throws Exception {
        Cookie c = null;
        List<String> created = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            String time = "%02d:%s".formatted(9 + i / 2, i % 2 == 0 ? "00" : "30");
            Created r = create("manicure", "2026-11-06", time, c);
            created.add(r.id());
            c = r.cookie();
        }
        List<String> listed = ids(mine(c));
        assertThat(listed).hasSize(10).doesNotContain(created.getFirst()).contains(created.getLast());
    }

    @Test
    void sessaoDoPainelNaoVaziaComoCookieDeAcompanhamento() throws Exception {
        Cookie session = master();
        assertThat(ids(mine(new Cookie("my_requests", session.getValue())))).isEmpty();
    }
}
