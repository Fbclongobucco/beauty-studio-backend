package com.longobuccodev.beautystudio.gateway;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.stripPrefix;
import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.CircuitBreakerFilterFunctions.circuitBreaker;
import static org.springframework.cloud.gateway.server.mvc.filter.LoadBalancerFilterFunctions.lb;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.web.servlet.function.RequestPredicates.GET;
import static org.springframework.web.servlet.function.RequestPredicates.HEAD;
import static org.springframework.web.servlet.function.RequestPredicates.path;

/**
 * Rotas do gateway. Cada uma tem circuit breaker com fallback local em JSON (503 SERVICE_UNAVAILABLE).
 * <ul>
 *   <li>{@code /api/**}: booking-service, localizado pelo Eureka;</li>
 *   <li>{@code /media/**}: fotos publicas no MinIO, so GET e HEAD, sem o prefixo {@code /media}.</li>
 * </ul>
 */
@Configuration
public class GatewayRoutesConfig {

    static final String FALLBACK = "forward:" + FallbackController.PATH;

    @Bean
    RouterFunction<ServerResponse> bookingRoute() {
        return route("booking-service")
                .route(path("/api/**"), http())
                // Circuit breaker por fora do load balancer: sem instância registrada também cai no fallback 503
                .filter(circuitBreaker(c -> c.setId("booking-service").setFallbackUri(FALLBACK)))
                .filter(lb("booking-service"))
                .build();
    }

    @Bean
    RouterFunction<ServerResponse> mediaRoute(GatewayProperties properties) {
        return route("media")
                .route(GET("/media/**").or(HEAD("/media/**")), http())
                .before(uri(properties.mediaUri()))
                .before(stripPrefix(1))
                .filter(circuitBreaker(c -> c.setId("media").setFallbackUri(FALLBACK)))
                .build();
    }

    /**
     * Cliente HTTP do proxy: HttpClient do JDK, sem armazenamento de cookies. O Apache HttpClient 5, que vem no
     * classpath com o cliente do Eureka, guarda os cookies das respostas e os reenviaria para outras pessoas
     * (vazamento de sessão entre clientes). Sem seguir redirecionamentos: o navegador decide.
     */
    @Bean
    ClientHttpRequestFactory gatewayClientHttpRequestFactory(GatewayProperties properties) {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(client);
        factory.setReadTimeout(properties.timeout());
        return factory;
    }

    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> circuitBreakerDefaults(GatewayProperties properties) {
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(properties.timeout()).build())
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(properties.openStateWait())
                        .permittedNumberOfCallsInHalfOpenState(2)
                        .build())
                .build());
    }
}
