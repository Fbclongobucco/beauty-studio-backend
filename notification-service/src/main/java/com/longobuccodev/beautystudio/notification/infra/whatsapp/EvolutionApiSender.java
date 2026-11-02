package com.longobuccodev.beautystudio.notification.infra.whatsapp;

import com.longobuccodev.beautystudio.notification.core.domain.Phones;
import com.longobuccodev.beautystudio.notification.core.exception.PermanentSendException;
import com.longobuccodev.beautystudio.notification.core.exception.TransientSendException;
import com.longobuccodev.beautystudio.notification.core.gateway.WhatsAppSender;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import org.springframework.cloud.client.circuitbreaker.CircuitBreaker;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;

/**
 * Evolution API v2 ({@code POST /message/sendText/{instancia}}, cabeçalho {@code apikey}), conferida no código da
 * versão 2.3.7. Resposta 400 = número inválido ou sem WhatsApp (permanente); demais falhas, tempo esgotado e
 * circuito aberto são temporárias.
 */
public class EvolutionApiSender implements WhatsAppSender {

    private final RestClient client;
    private final String instance;
    private final CircuitBreaker breaker;

    public EvolutionApiSender(RestClient client, String instance, CircuitBreakerFactory<?, ?> breakers) {
        this.client = client;
        this.instance = instance;
        this.breaker = breakers.create("evolution-api");
    }

    @Override
    public SendResult send(String phoneE164, String text) {
        return breaker.run(() -> call(phoneE164, text), EvolutionApiSender::translate);
    }

    private SendResult call(String phoneE164, String text) {
        try {
            Map<?, ?> body = client.post()
                    .uri("/message/sendText/{instance}", instance)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("number", Phones.digits(phoneE164), "text", text))
                    .retrieve()
                    .body(Map.class);
            return new SendResult(messageId(body), false);
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().value() == HttpStatus.BAD_REQUEST.value()) {
                throw new PermanentSendException("Número inválido ou sem WhatsApp ("
                        + Phones.mask(phoneE164) + "): " + excerpt(e.getResponseBodyAsString()));
            }
            throw new TransientSendException("Evolution API respondeu " + e.getStatusCode().value(), e);
        } catch (RestClientException e) {
            throw new TransientSendException("Evolution API indisponível: " + e.getMessage(), e);
        }
    }

    private static SendResult translate(Throwable error) {
        Throwable t = error;
        while ((t instanceof CompletionException || t instanceof ExecutionException) && t.getCause() != null) {
            t = t.getCause();
        }
        if (t instanceof PermanentSendException p) {
            throw p;
        }
        if (t instanceof TransientSendException s) {
            throw s;
        }
        if (t instanceof CallNotPermittedException) {
            throw new TransientSendException("Circuito aberto: Evolution API com falhas recentes", t);
        }
        throw new TransientSendException("Falha ao chamar a Evolution API: " + t, t);
    }

    private static String messageId(Map<?, ?> body) {
        if (body != null && body.get("key") instanceof Map<?, ?> key && key.get("id") instanceof String id) {
            return id;
        }
        return null;
    }

    private static String excerpt(String body) {
        return body == null || body.length() <= 200 ? body : body.substring(0, 200);
    }
}
