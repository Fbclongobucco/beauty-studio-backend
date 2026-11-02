package com.longobuccodev.beautystudio.booking.infra;

import com.longobuccodev.beautystudio.booking.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 8.7 (ganhos) e 8.8 (formato de erro por código). */
class EarningsAndErrorsIT extends IntegrationTest {

    void act(Cookie session, String id, String action) throws Exception {
        mvc.perform(json(post("/api/admin/appointments/" + id + "/" + action).cookie(session), "{}"))
                .andExpect(status().isOk());
    }

    @Test
    void canceladoForaDaSoma() throws Exception {
        Cookie stefani = stefani();
        act(stefani, book("manicure", "2026-11-03", "10:00"), "confirm");
        act(stefani, book("manicure", "2026-11-04", "10:00"), "confirm");
        String pedicure = book("pedicure", "2026-11-05", "10:00");
        act(stefani, pedicure, "confirm");
        act(stefani, pedicure, "cancel");
        book("pedicure", "2026-11-05", "11:00");

        mvc.perform(get("/api/admin/earnings?from=2026-11-01&to=2026-11-30").cookie(stefani))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total.cents").value(4000))
                .andExpect(jsonPath("$.total.count").value(2))
                .andExpect(jsonPath("$.expected.cents").value(4000))
                .andExpect(jsonPath("$.unpriced").value(0))
                .andExpect(jsonPath("$.byService[0].name").value("Manicure"))
                .andExpect(jsonPath("$.byDay[0].date").value("2026-11-02"))
                .andExpect(jsonPath("$.byProfessional[0].professionalId").value("stefani"));
    }

    @Test
    void profissionalVeSoOProprioEMasterFiltra() throws Exception {
        act(lorrayne(), book("box-braids", "2026-11-03", "09:00"), "confirm");
        act(stefani(), book("manicure", "2026-11-03", "10:00"), "confirm");
        mvc.perform(get("/api/admin/earnings?from=2026-11-01&to=2026-11-30&professionalId=lorrayne").cookie(michele()))
                .andExpect(jsonPath("$.total.cents").value(0));
        mvc.perform(get("/api/admin/earnings?from=2026-11-01&to=2026-11-30").cookie(master()))
                .andExpect(jsonPath("$.total.cents").value(21000))
                .andExpect(jsonPath("$.byProfessional.length()").value(2));
        mvc.perform(get("/api/admin/earnings?from=2026-11-01&to=2026-11-30&professionalId=lorrayne").cookie(master()))
                .andExpect(jsonPath("$.total.cents").value(19000));
    }

    @Test
    void periodoInvalido() throws Exception {
        Cookie master = master();
        mvc.perform(get("/api/admin/earnings?from=2026-11-20&to=2026-11-10").cookie(master))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fields.to").value("A data final deve ser depois da inicial"));
        mvc.perform(get("/api/admin/earnings?from=2026-01-01&to=2027-01-02").cookie(master))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.to").value("O período pode ter no máximo 366 dias."));
    }

    @Test
    void formatoDeErroPorCodigo() throws Exception {
        // VALIDATION_ERROR com fields; corpo que não é JSON também
        mvc.perform(post("/api/appointments").contentType(MediaType.APPLICATION_JSON).content("{nao json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("Confira os campos destacados."));
        // NOT_FOUND sem fields
        mvc.perform(get("/api/appointments/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.fields").doesNotExist());
        mvc.perform(get("/api/nao-existe")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
        // UNAUTHORIZED
        mvc.perform(get("/api/admin/earnings")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Faça login para continuar."));
        // FORBIDDEN
        mvc.perform(json(post("/api/admin/categories").cookie(stefani()), "{\"name\":\"X\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message").value("Você não tem permissão para esta ação."));
    }
}
