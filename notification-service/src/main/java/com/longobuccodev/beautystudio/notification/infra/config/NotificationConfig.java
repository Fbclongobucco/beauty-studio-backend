package com.longobuccodev.beautystudio.notification.infra.config;

import com.longobuccodev.beautystudio.messaging.application.EventHandler;
import com.longobuccodev.beautystudio.messaging.core.AppointmentEvent;
import com.longobuccodev.beautystudio.messaging.infra.MessagingProperties;
import com.longobuccodev.beautystudio.notification.application.usecase.AppointmentEventDispatcher;
import com.longobuccodev.beautystudio.notification.application.usecase.NotificationDelivery;
import com.longobuccodev.beautystudio.notification.application.usecase.NotifyCustomerOnStatusChange;
import com.longobuccodev.beautystudio.notification.application.usecase.NotifyProfessionalOnCreated;
import com.longobuccodev.beautystudio.notification.core.exception.PermanentSendException;
import com.longobuccodev.beautystudio.notification.core.gateway.WhatsAppSender;
import com.longobuccodev.beautystudio.notification.core.repository.NotificationRepository;
import com.longobuccodev.beautystudio.notification.infra.messaging.NotificationEventHandler;
import com.longobuccodev.beautystudio.notification.infra.whatsapp.EvolutionApiSender;
import com.longobuccodev.beautystudio.notification.infra.whatsapp.LogOnlySender;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;

/** Casos de uso (Java puro) e adaptadores do notification-service. */
@Configuration
public class NotificationConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    /** Envio real pela Evolution API, ou só log quando {@code NOTIFICATION_SEND_ENABLED=false}. */
    @Bean
    WhatsAppSender whatsAppSender(NotificationProperties properties, RestClient.Builder builder,
                                  CircuitBreakerFactory<?, ?> breakers) {
        if (!properties.sendEnabled()) {
            return new LogOnlySender();
        }
        NotificationProperties.Evolution evo = properties.evolution();
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(evo.timeout());
        RestClient client = builder.baseUrl(evo.baseUrl().toString())
                .defaultHeader("apikey", evo.apiKey())
                .requestFactory(factory)
                .build();
        return new EvolutionApiSender(client, evo.instance(), breakers);
    }

    @Bean
    NotificationDelivery notificationDelivery(NotificationRepository repository, WhatsAppSender sender, Clock clock,
                                              MessagingProperties messaging) {
        return new NotificationDelivery(repository, sender, clock, messaging.consumer().maxAttempts());
    }

    @Bean
    AppointmentEventDispatcher appointmentEventDispatcher(NotificationDelivery delivery,
                                                          NotificationProperties properties) {
        return new AppointmentEventDispatcher(new NotifyProfessionalOnCreated(delivery, properties.frontUrl()),
                new NotifyCustomerOnStatusChange(delivery, properties.salonWhatsapp()));
    }

    @Bean
    EventHandler<AppointmentEvent> notificationEventHandler(AppointmentEventDispatcher dispatcher) {
        return new NotificationEventHandler(dispatcher);
    }

    /** Erro permanente (número sem WhatsApp) não conta como falha do provedor para abrir o circuito. */
    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> circuitBreakerDefaults(NotificationProperties properties) {
        Duration timeout = properties.evolution().timeout().plusSeconds(5);
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(timeout).build())
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(30))
                        .ignoreExceptions(PermanentSendException.class)
                        .build())
                .build());
    }
}
