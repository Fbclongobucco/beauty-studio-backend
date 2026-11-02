package com.longobuccodev.beautystudio.booking.infra.messaging;

import com.longobuccodev.beautystudio.booking.infra.config.BookingProperties;
import com.longobuccodev.beautystudio.messaging.application.EventPublisher;
import com.longobuccodev.beautystudio.messaging.infra.AppointmentEventCodec;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;

@Configuration
public class OutboxScheduling {

    @Bean
    OutboxRelay outboxRelay(JdbcClient jdbc, TransactionTemplate tx, EventPublisher publisher,
                            AppointmentEventCodec codec, Clock clock, BookingProperties properties) {
        return new OutboxRelay(jdbc, tx, publisher, codec, clock, properties.outbox());
    }

    /** Agendamento do relay e da limpeza; desligável por {@code beauty.outbox.relay-enabled=false} (testes). */
    @Configuration
    @EnableScheduling
    @ConditionalOnProperty(prefix = "beauty.outbox", name = "relay-enabled", havingValue = "true",
            matchIfMissing = true)
    static class Jobs {

        private final OutboxRelay relay;

        Jobs(OutboxRelay relay) {
            this.relay = relay;
        }

        @Scheduled(fixedDelayString = "${beauty.outbox.relay-interval:1s}")
        void relay() {
            relay.relayOnce();
        }

        @Scheduled(cron = "0 30 3 * * *", zone = "America/Sao_Paulo")
        void cleanup() {
            relay.cleanup();
        }
    }
}
