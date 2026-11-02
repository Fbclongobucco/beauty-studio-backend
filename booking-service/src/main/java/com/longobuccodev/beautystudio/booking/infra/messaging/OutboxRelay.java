package com.longobuccodev.beautystudio.booking.infra.messaging;

import com.longobuccodev.beautystudio.booking.infra.config.BookingProperties;
import com.longobuccodev.beautystudio.messaging.application.EventPublisher;
import com.longobuccodev.beautystudio.messaging.infra.AppointmentEventCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Publica os eventos pendentes do outbox na ordem de gravação. Linhas lidas com {@code FOR UPDATE SKIP LOCKED}:
 * seguro com várias instâncias. Um evento só é marcado como publicado depois da confirmação do broker; com falha,
 * os eventos seguintes do mesmo agendamento esperam a próxima rodada, preservando a ordem.
 */
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final EventPublisher publisher;
    private final AppointmentEventCodec codec;
    private final Clock clock;
    private final BookingProperties.Outbox config;

    public OutboxRelay(JdbcClient jdbc, TransactionTemplate tx, EventPublisher publisher, AppointmentEventCodec codec,
                       Clock clock, BookingProperties.Outbox config) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.publisher = publisher;
        this.codec = codec;
        this.clock = clock;
        this.config = config;
    }

    record Row(UUID id, String aggregateId, String type, String payload) {
    }

    /** Uma rodada do relay; retorna quantos eventos foram publicados. */
    public int relayOnce() {
        Integer published = tx.execute(status -> {
            List<Row> rows = jdbc.sql("""
                            select id, aggregate_id, type, payload::text as payload from outbox_event
                            where published_at is null order by seq limit :limit for update skip locked""")
                    .param("limit", config.batchSize())
                    .query((rs, n) -> new Row(rs.getObject("id", UUID.class), rs.getString("aggregate_id"),
                            rs.getString("type"), rs.getString("payload")))
                    .list();
            Set<String> blocked = new HashSet<>();
            int ok = 0;
            boolean logged = false;
            for (Row row : rows) {
                if (blocked.contains(row.aggregateId())) {
                    continue;
                }
                try {
                    publisher.publish(codec.decode(row.payload().getBytes(StandardCharsets.UTF_8)));
                    jdbc.sql("update outbox_event set published_at = :now, attempts = attempts + 1, last_error = null"
                                    + " where id = :id")
                            .param("now", Timestamp.from(clock.instant())).param("id", row.id()).update();
                    ok++;
                } catch (RuntimeException e) {
                    blocked.add(row.aggregateId());
                    jdbc.sql("update outbox_event set attempts = attempts + 1, last_error = :error where id = :id")
                            .param("error", truncate(e.getMessage())).param("id", row.id()).update();
                    if (!logged) {
                        log.warn("Evento {} ({}) nao publicado, nova tentativa na proxima rodada: {}", row.id(),
                                row.type(), e.getMessage());
                        logged = true;
                    }
                }
            }
            return ok;
        });
        return published == null ? 0 : published;
    }

    /** Remove eventos publicados há mais tempo que a retenção configurada. */
    public int cleanup() {
        return jdbc.sql("delete from outbox_event where published_at is not null and published_at < :limit")
                .param("limit", Timestamp.from(clock.instant().minus(config.retention())))
                .update();
    }

    public long pendingCount() {
        return jdbc.sql("select count(*) from outbox_event where published_at is null").query(Long.class).single();
    }

    private static String truncate(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > 500 ? message.substring(0, 500) : message;
    }
}
