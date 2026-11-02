package com.longobuccodev.beautystudio.booking.infra;

import com.longobuccodev.beautystudio.booking.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 8.3 e 8.4: painel de pedidos, conflito, "Novo" e transições de status. */
class AdminAppointmentsIT extends IntegrationTest {

    static final JsonMapper MAPPER = JsonMapper.builder().build();

    JsonNode list(Cookie session, String query) throws Exception {
        return MAPPER.readTree(mvc.perform(get("/api/admin/appointments" + query).cookie(session))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("appointments");
    }

    int confirm(Cookie session, String id) throws Exception {
        return mvc.perform(json(post("/api/admin/appointments/" + id + "/confirm").cookie(session), "{}"))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void listaTrazConflictComoNoContratoDoFront() throws Exception {
        String a = book("manicure", "2026-11-06", "11:00");
        String b = book("manicure", "2026-11-06", "11:00");
        book("manicure", "2026-11-06", "11:30");
        Cookie stefani = stefani();

        JsonNode before = list(stefani, "?date=2026-11-06");
        assertThat(before.get(0).has("conflict")).isTrue();
        assertThat(before).extracting(x -> x.get("conflict").asString("null"))
                .containsExactly("pending", "pending", "null");

        assertThat(confirm(stefani, a)).isEqualTo(200);
        JsonNode after = list(stefani, "?status=pending");
        assertThat(after).hasSize(2);
        assertThat(after.get(0).get("id").asString()).isEqualTo(b);
        assertThat(after.get(0).get("conflict").asString()).isEqualTo("confirmed");
        assertThat(after.get(1).get("conflict").isNull()).isTrue();

        mvc.perform(json(post("/api/admin/appointments/" + b + "/confirm").cookie(stefani), "{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SLOT_TAKEN"))
                .andExpect(jsonPath("$.message").value("Horário já ocupado por outro agendamento confirmado"));
    }

    @Test
    void filtrosDeIntervaloEValidacao() throws Exception {
        book("manicure", "2026-11-06", "11:00");
        book("manicure", "2026-11-10", "11:00");
        Cookie master = master();
        assertThat(list(master, "?from=2026-11-06&to=2026-11-06")).hasSize(1);
        assertThat(list(master, "?from=2026-11-01&to=2026-11-30&professionalId=stefani")).hasSize(2);
        assertThat(list(master, "?professionalId=lorrayne")).isEmpty();
        mvc.perform(get("/api/admin/appointments?status=x&from=2026-13-01").cookie(master))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.status").value("Status inválido."))
                .andExpect(jsonPath("$.fields.from").value("Data inválida (use AAAA-MM-DD)."));
    }

    @Test
    void contagemENovoPorProfissionalEMasterNaoMarcaComoVisto() throws Exception {
        String box = book("box-braids", "2026-11-06", "09:00");
        book("manicure", "2026-11-06", "11:00");
        Cookie master = master();
        Cookie lorrayne = lorrayne();

        mvc.perform(get("/api/admin/appointments/pending-count").cookie(master))
                .andExpect(jsonPath("$.pending").value(2)).andExpect(jsonPath("$.unseen").value(2));
        mvc.perform(get("/api/admin/appointments/pending-count").cookie(stefani()))
                .andExpect(jsonPath("$.pending").value(1));

        mvc.perform(json(post("/api/admin/appointments/mark-seen").cookie(master), "{}"))
                .andExpect(jsonPath("$.marked").value(0));
        mvc.perform(get("/api/admin/appointments/pending-count").cookie(lorrayne))
                .andExpect(jsonPath("$.unseen").value(1));

        mvc.perform(json(post("/api/admin/appointments/mark-seen").cookie(lorrayne), "{\"ids\":[\"" + box + "\"]}"))
                .andExpect(jsonPath("$.marked").value(1));
        mvc.perform(get("/api/admin/appointments/pending-count").cookie(lorrayne))
                .andExpect(jsonPath("$.pending").value(1)).andExpect(jsonPath("$.unseen").value(0));
        mvc.perform(get("/api/admin/appointments/pending-count").cookie(master))
                .andExpect(jsonPath("$.unseen").value(1));
    }

    @Test
    void transicoesComHistoricoEventosEMotivo() throws Exception {
        String id = book("box-braids", "2026-11-06", "09:00");
        Cookie lorrayne = lorrayne();
        assertThat(confirm(lorrayne, id)).isEqualTo(200);
        mvc.perform(json(post("/api/admin/appointments/" + id + "/cancel").cookie(lorrayne),
                        "{\"reason\":\"  Imprevisto de saúde \"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("cancelled"))
                .andExpect(jsonPath("$.reason").value("Imprevisto de saúde"))
                .andExpect(jsonPath("$.cancelledAt").isNotEmpty());

        assertThat(jdbc.sql("select from_status || '>' || to_status || ':' || actor from appointment_status_history"
                + " order by id").query(String.class).list())
                .containsExactly("pending>confirmed:lorrayne", "confirmed>cancelled:lorrayne");
        assertThat(outboxCount("appointment.confirmed")).isEqualTo(1);
        assertThat(outboxCount("appointment.cancelled")).isEqualTo(1);

        // Cancelamento libera o horário
        assertThat(mvc.perform(get("/api/availability?serviceId=box-braids&date=2026-11-06"))
                .andReturn().getResponse().getContentAsString()).contains("\"09:00\"");
    }

    @Test
    void transicaoInvalidaEAcaoDesconhecida() throws Exception {
        String id = book("manicure", "2026-11-06", "11:00");
        Cookie stefani = stefani();
        mvc.perform(json(post("/api/admin/appointments/" + id + "/reject").cookie(stefani), "{}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("rejected"));
        mvc.perform(json(post("/api/admin/appointments/" + id + "/confirm").cookie(stefani), "{}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("INVALID_TRANSITION"));
        mvc.perform(json(post("/api/admin/appointments/" + id + "/apagar").cookie(stefani), "{}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.message").value("Ação desconhecida."));
        assertThat(outboxCount("appointment.confirmed")).isZero();
    }

    @Test
    void confirmacoesSimultaneasAceitamSoUmaEGeramUmEvento() throws Exception {
        for (int round = 0; round < 5; round++) {
            String date = "2026-11-" + (10 + round);
            String a = book("manicure", date, "11:00");
            String b = book("manicure", date, "11:00");
            Cookie s1 = stefani();
            Cookie s2 = stefani();
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor();
            List<Future<Integer>> results = new ArrayList<>();
            for (String[] job : new String[][]{{a}, {b}}) {
                Cookie session = job[0].equals(a) ? s1 : s2;
                results.add(pool.submit(() -> {
                    start.await();
                    return confirm(session, job[0]);
                }));
            }
            start.countDown();
            List<Integer> codes = new ArrayList<>();
            for (Future<Integer> f : results) {
                codes.add(f.get());
            }
            pool.shutdown();
            assertThat(codes).containsExactlyInAnyOrder(200, 409);
            assertThat(jdbc.sql("select count(*) from appointment where status = 'confirmed' and date = cast(:d as date)")
                    .param("d", date).query(Long.class).single()).isEqualTo(1);
        }
        assertThat(outboxCount("appointment.confirmed")).isEqualTo(5);
    }
}
