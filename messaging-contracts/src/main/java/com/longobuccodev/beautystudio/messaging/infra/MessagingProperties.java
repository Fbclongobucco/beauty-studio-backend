package com.longobuccodev.beautystudio.messaging.infra;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

/**
 * Configuracao da mensageria ({@code beauty.messaging.*}).
 *
 * @param publishConfirmTimeout espera maxima pela confirmacao do broker ao publicar
 * @param consumer              fila consumidora; sem {@code queue}, o servico so publica
 */
@ConfigurationProperties("beauty.messaging")
public record MessagingProperties(Duration publishConfirmTimeout, Consumer consumer) {

    public MessagingProperties {
        publishConfirmTimeout = publishConfirmTimeout == null ? Duration.ofSeconds(5) : publishConfirmTimeout;
        consumer = consumer == null ? new Consumer(null, null, null, 0, 0) : consumer;
    }

    /**
     * @param queue       nome da fila principal; as filas {@code <queue>.retry.N} e {@code <queue>.dlq} derivam dele
     * @param routingKeys routing keys ligadas a fila (padrao {@code appointment.*})
     * @param retryDelays espera antes de cada nova tentativa; a ultima se repete
     * @param maxAttempts tentativas no total antes da DLQ
     * @param prefetch    mensagens entregues ao consumidor sem ack
     */
    public record Consumer(String queue, List<String> routingKeys, List<Duration> retryDelays, int maxAttempts,
                           int prefetch) {

        public Consumer {
            routingKeys = routingKeys == null || routingKeys.isEmpty() ? List.of("appointment.*") : List.copyOf(routingKeys);
            retryDelays = retryDelays == null || retryDelays.isEmpty()
                    ? List.of(Duration.ofSeconds(10), Duration.ofMinutes(1), Duration.ofMinutes(5))
                    : List.copyOf(retryDelays);
            maxAttempts = maxAttempts <= 0 ? 5 : maxAttempts;
            prefetch = prefetch <= 0 ? 5 : prefetch;
        }

        public String retryQueue(int index) {
            return queue + ".retry." + (index + 1);
        }

        public String deadLetterQueue() {
            return queue + ".dlq";
        }

        /** Espera antes da tentativa seguinte a falha numero {@code failures} (1, 2, ...). */
        public int retryIndex(int failures) {
            return Math.min(failures - 1, retryDelays.size() - 1);
        }
    }
}
