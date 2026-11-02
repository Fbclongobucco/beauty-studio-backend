package com.longobuccodev.beautystudio.booking.infra;

import com.longobuccodev.beautystudio.booking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 6.1 e 6.2: esquema, regra de confirmados sobrepostos no banco e catálogo inicial. */
class MigrationIT extends IntegrationTest {

    @Test
    void historicoDoFlywayRegistraCadaVersao() {
        List<String> versions = jdbc.sql("select version from flyway_schema_history where success order by installed_rank")
                .query(String.class).list();
        assertThat(versions).containsExactly("1", "2", "3", "4");
    }

    private void insert(String status, String start, String end) {
        jdbc.sql("""
                        insert into appointment (id, service_id, professional_id, service_name, professional_name,
                          duration_min, price_cents, date, time, starts_at, ends_at, customer_name, customer_phone,
                          status, created_at)
                        values (:id, 'manicure', 'stefani', 'Manicure', 'Stéfani', 30, 2000, '2026-11-06', :time,
                          cast(:start as timestamptz), cast(:end as timestamptz), 'Maria', '+5521988887777', :status,
                          now())""")
                .param("id", UUID.randomUUID()).param("time", java.sql.Time.valueOf(start.substring(11, 19)))
                .param("start", start).param("end", end).param("status", status).update();
    }

    @Test
    void doisConfirmadosSobrepostosDaMesmaProfissionalViolamAExclusionConstraint() {
        insert("confirmed", "2026-11-06T11:00:00-03:00", "2026-11-06T12:00:00-03:00");
        insert("pending", "2026-11-06T11:30:00-03:00", "2026-11-06T12:00:00-03:00");
        insert("confirmed", "2026-11-06T12:00:00-03:00", "2026-11-06T12:30:00-03:00");
        assertThatThrownBy(() -> insert("confirmed", "2026-11-06T11:30:00-03:00", "2026-11-06T12:00:00-03:00"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .satisfies(e -> assertThat(sqlState(e)).isEqualTo("23P01"));
    }

    private static String sqlState(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql) {
                return sql.getSQLState();
            }
        }
        return null;
    }

    @Test
    void catalogoInicialIgualATabelaDaSpecServiceCatalog() {
        assertThat(jdbc.sql("select id from professional order by created_at").query(String.class).list())
                .containsExactly("stefani", "lorrayne", "michele");
        assertThat(jdbc.sql("select count(*) from service where active").query(Long.class).single()).isEqualTo(21);
        assertThat(jdbc.sql("select note from category where id = 'trancas'").query(String.class).single())
                .isEqualTo("Valores somente de mão de obra.");
        // V4 tirou o aviso de Francesinha/Encapsular, a pedido do salão
        assertThat(jdbc.sql("select note from category where id = 'unhas'").query().singleRow())
                .containsEntry("note", null);

        Map<String, Object> box = jdbc.sql("select * from service where id = 'box-braids'").query().singleRow();
        assertThat(box).containsEntry("price_cents", 19000).containsEntry("duration_min", 360)
                .containsEntry("duration_estimated", true).containsEntry("professional_id", "lorrayne")
                .containsEntry("subcategory", "Estilos");
        Map<String, Object> manicure = jdbc.sql("select * from service where id = 'manicure'").query().singleRow();
        assertThat(manicure).containsEntry("price_cents", 2000).containsEntry("duration_min", 30)
                .containsEntry("duration_estimated", false);
        Map<String, Object> depilacao = jdbc.sql("select * from service where id = 'depilacao'").query().singleRow();
        assertThat(depilacao).containsEntry("price_cents", 8000).containsEntry("duration_min", 60)
                .containsEntry("duration_estimated", false).containsEntry("subcategory", null);
        // Informadas: Manicure, Pedicure, Manicure + Pedicure e Depilação; as outras 17 são estimativa
        assertThat(jdbc.sql("select count(*) from service where duration_estimated").query(Long.class).single())
                .isEqualTo(17);
        assertThat(jdbc.sql("select sum(price_cents) from service").query(Long.class).single())
                .isEqualTo((20 + 25 + 40 + 50 + 40 + 85 + 70 + 90 + 115 + 135 + 85 + 110
                        + 35 + 45 + 60 + 190 + 140 + 200 + 160 + 160 + 80) * 100L);
    }
}
