package com.longobuccodev.beautystudio.booking.infra;

import com.longobuccodev.beautystudio.booking.application.usecase.AccountUseCase;
import com.longobuccodev.beautystudio.booking.support.IntegrationTest;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Seção 7: contas, login, sessão por cookie, escopo por perfil e limite de tentativas (spec admin-auth). */
class AuthIT extends IntegrationTest {

    @Autowired
    AccountUseCase accounts;

    MockHttpServletResponse loginResponse(String user, String password) throws Exception {
        return mvc.perform(json(post("/api/admin/login").header("X-Forwarded-For", "10.0.0." + user.length()),
                "{\"username\":\"" + user + "\",\"password\":\"" + password + "\"}")).andReturn().getResponse();
    }

    @Test
    void senhasGuardadasSoComoHashBcrypt() {
        List<String> hashes = jdbc.sql("select password_hash from account").query(String.class).list();
        assertThat(hashes).hasSize(4).allSatisfy(h -> assertThat(h).startsWith("$2").doesNotContain("master123"));
    }

    @Test
    void loginDoMasterCriaCookieHttpOnlyDe12Horas() throws Exception {
        MockHttpServletResponse res = loginResponse("  MASTER ", "master123");
        assertThat(res.getStatus()).isEqualTo(200);
        assertThat(res.getContentAsString()).isEqualTo("{\"ok\":true}");
        String header = res.getHeader("Set-Cookie");
        assertThat(header).startsWith("admin_session=").contains("HttpOnly").contains("Max-Age=43200")
                .contains("Path=/").contains("SameSite=Lax");
    }

    @Test
    void usuarioInexistenteESenhaErradaTemAMesmaMensagemESemCookie() throws Exception {
        for (MockHttpServletResponse res : List.of(loginResponse("maria", "x"), loginResponse("stefani", "errada"))) {
            assertThat(res.getStatus()).isEqualTo(401);
            assertThat(res.getContentAsString()).contains("\"code\":\"UNAUTHORIZED\"")
                    .contains("Usuário ou senha incorretos");
            assertThat(res.getHeader("Set-Cookie")).isNull();
        }
    }

    @Test
    void semContasOLoginFicaDesabilitado() throws Exception {
        jdbc.sql("delete from account").update();
        MockHttpServletResponse res = loginResponse("master", "master123");
        assertThat(res.getStatus()).isEqualTo(503);
        assertThat(res.getContentAsString()).contains("LOGIN_DISABLED");
    }

    @Test
    void meDaProfissionalEDoMaster() throws Exception {
        mvc.perform(get("/api/admin/me").cookie(lorrayne())).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("professional"))
                .andExpect(jsonPath("$.professionalId").value("lorrayne"))
                .andExpect(jsonPath("$.name").value("Lorrayne Longobuco"))
                .andExpect(jsonPath("$.whatsappPhone").isEmpty());
        mvc.perform(get("/api/admin/me").cookie(master())).andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("master"))
                .andExpect(jsonPath("$.professionalId").isEmpty())
                .andExpect(jsonPath("$.name").value(""));
    }

    @Test
    void semSessaoOuSessaoExpiradaResponde401() throws Exception {
        mvc.perform(get("/api/admin/appointments")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        Cookie session = stefani();
        clock.advance(Duration.ofHours(12).plusSeconds(1));
        mvc.perform(get("/api/admin/me").cookie(session)).andExpect(status().isUnauthorized());
    }

    @Test
    void logoutApagaOCookie() throws Exception {
        String header = mvc.perform(post("/api/admin/logout")).andExpect(status().isOk())
                .andReturn().getResponse().getHeader("Set-Cookie");
        assertThat(header).startsWith("admin_session=;").contains("Max-Age=0");
    }

    @Test
    void trocaDeSenhaPeloPainelDerrubaSoAsSessoesDaConta() throws Exception {
        Cookie master = master();
        mvc.perform(json(post("/api/admin/professionals").cookie(master), "{\"name\":\"Ana Souza\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value("ana"));
        mvc.perform(json(post("/api/admin/accounts").cookie(master), "{\"professionalId\":\"ana\",\"password\":\"ana123\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.login").value("db"));
        Cookie ana = login("ana", "ana123");
        Cookie stefani = stefani();

        mvc.perform(json(post("/api/admin/accounts").cookie(master), "{\"professionalId\":\"ana\",\"password\":\"nova456\"}"))
                .andExpect(status().isCreated());
        mvc.perform(get("/api/admin/me").cookie(ana)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/me").cookie(stefani)).andExpect(status().isOk());
        login("ana", "nova456");
    }

    @Test
    void trocaDeSenhaNoAmbienteDerrubaASessao() throws Exception {
        Cookie michele = michele();
        accounts.syncFromEnvironment("master123", "stefani:stefani1;lorrayne:lorrayne1;michele:outra");
        mvc.perform(get("/api/admin/me").cookie(michele)).andExpect(status().isUnauthorized());
        login("michele", "outra");
    }

    @Test
    void loginCriadoPeloPainelPersisteENaoESobrescritoPelaSubida() throws Exception {
        Cookie master = master();
        mvc.perform(json(post("/api/admin/professionals").cookie(master), "{\"name\":\"Ana Souza\"}"));
        mvc.perform(json(post("/api/admin/accounts").cookie(master), "{\"professionalId\":\"ana\",\"password\":\"ana123\"}"));
        // Nova subida do serviço: a sincronização com o ambiente não remove o login do painel
        accounts.syncFromEnvironment("master123", "stefani:stefani1;lorrayne:lorrayne1;michele:michele1");
        login("ana", "ana123");
        mvc.perform(get("/api/admin/accounts").cookie(master)).andExpect(status().isOk())
                .andExpect(jsonPath("$.masterUsername").value("master"))
                .andExpect(jsonPath("$.accounts[?(@.professionalId=='ana')].login").value("db"))
                .andExpect(jsonPath("$.accounts[?(@.professionalId=='stefani')].login").value("env"))
                .andExpect(jsonPath("$..password").doesNotExist())
                .andExpect(jsonPath("$..passwordHash").doesNotExist());
    }

    @Test
    void contaDoAmbienteNaoPodeSerAlteradaPeloPainel() throws Exception {
        mvc.perform(json(post("/api/admin/accounts").cookie(master()),
                        "{\"professionalId\":\"stefani\",\"password\":\"outra123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.professionalId").value("A senha dessa profissional é definida no servidor (ADMIN_USERS)."));
        login("stefani", "stefani1");
    }

    @Test
    void senhaCurtaRecusada() throws Exception {
        Cookie master = master();
        mvc.perform(json(post("/api/admin/professionals").cookie(master), "{\"name\":\"Ana Souza\"}"));
        mvc.perform(json(post("/api/admin/accounts").cookie(master), "{\"professionalId\":\"ana\",\"password\":\"123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").value("A senha deve ter pelo menos 6 caracteres."));
    }

    @Autowired
    com.longobuccodev.beautystudio.booking.core.repository.AccountRepository accountRepository;
    @Autowired
    com.longobuccodev.beautystudio.booking.core.repository.ProfessionalRepository professionalRepository;
    @Autowired
    com.longobuccodev.beautystudio.booking.core.security.PasswordHasher hasher;
    @Autowired
    com.longobuccodev.beautystudio.booking.application.port.TransactionRunner tx;

    @Test
    void masterRenomeadoEntraComONovoUsuarioEOAntigoDeixaDeValer() throws Exception {
        new AccountUseCase(accountRepository, professionalRepository, hasher, tx, "Admin")
                .syncFromEnvironment("master123", "stefani:stefani1");
        login("admin", "master123");
        MockHttpServletResponse old = loginResponse("master", "master123");
        assertThat(old.getStatus()).isEqualTo(401);
        assertThat(old.getContentAsString()).contains("Usuário ou senha incorretos");
    }

    @Test
    void entradaDeAdminUsersSemProfissionalEIgnorada() {
        AccountUseCase.SyncReport report = accounts.syncFromEnvironment("master123",
                "stefani:stefani1;lorrayne:lorrayne1;michele:michele1;maria:x");
        assertThat(report.ignoredIds()).containsExactly("maria");
        assertThat(jdbc.sql("select count(*) from account where username = 'maria'").query(Long.class).single())
                .isZero();
    }

    @Test
    void semAdminPasswordOMasterFicaDesabilitado() throws Exception {
        accounts.syncFromEnvironment("", "stefani:stefani1");
        assertThat(loginResponse("master", "master123").getStatus()).isEqualTo(401);
        login("stefani", "stefani1");
        // Contas do ambiente que saíram de ADMIN_USERS deixam de existir
        assertThat(loginResponse("lorrayne", "lorrayne1").getStatus()).isEqualTo(401);
    }

    @Test
    void filtroDeOutraProfissionalEIgnoradoEAcaoEmPedidoAlheioDa404() throws Exception {
        String lorrayneAppt = book("box-braids", "2026-11-06", "09:00");
        book("manicure", "2026-11-06", "11:00");
        mvc.perform(get("/api/admin/appointments").param("professionalId", "lorrayne").cookie(stefani()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appointments.length()").value(1))
                .andExpect(jsonPath("$.appointments[0].professionalId").value("stefani"));
        mvc.perform(json(post("/api/admin/appointments/" + lorrayneAppt + "/confirm").cookie(michele()), "{}"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
        assertThat(jdbc.sql("select status from appointment where id = cast(:id as uuid)").param("id", lorrayneAppt)
                .query(String.class).single()).isEqualTo("pending");
    }

    @Test
    void acoesExclusivasDoMasterRespondem403() throws Exception {
        Cookie stefani = stefani();
        mvc.perform(json(post("/api/admin/professionals").cookie(stefani), "{\"name\":\"Ana\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(json(patch("/api/admin/categories/unhas").cookie(stefani), "{\"note\":\"x\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/accounts").cookie(stefani)).andExpect(status().isForbidden());
        mvc.perform(json(patch("/api/admin/services/box-braids").cookie(lorrayne()), "{\"professionalId\":\"stefani\"}"))
                .andExpect(status().isForbidden());
        assertThat(jdbc.sql("select professional_id from service where id = 'box-braids'").query(String.class).single())
                .isEqualTo("lorrayne");
    }

    @Test
    void origemForjadaNoInicioDoXForwardedForNaoEscapaDoLimite() throws Exception {
        for (int i = 0; i < 5; i++) {
            mvc.perform(json(post("/api/admin/login").header("X-Forwarded-For", "9.9.9." + i + ",10.1.1.1"),
                    "{\"username\":\"usuario" + i + "\",\"password\":\"x\"}"));
        }
        MockHttpServletResponse res = mvc.perform(json(post("/api/admin/login")
                        .header("X-Forwarded-For", "8.8.8.8,10.1.1.1"), "{\"username\":\"outro\",\"password\":\"x\"}"))
                .andReturn().getResponse();
        assertThat(res.getStatus()).isEqualTo(429);
    }

    @Test
    void tentativasAcimaDoLimiteRespondem429AteAJanelaExpirar() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertThat(loginResponse("master", "errada").getStatus()).isEqualTo(401);
        }
        MockHttpServletResponse blocked = loginResponse("master", "master123");
        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("Muitas tentativas");
        clock.advance(Duration.ofMinutes(16));
        assertThat(loginResponse("master", "master123").getStatus()).isEqualTo(200);
    }
}
