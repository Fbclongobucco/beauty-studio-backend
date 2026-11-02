package com.longobuccodev.beautystudio.booking.infra.security;

import com.longobuccodev.beautystudio.booking.application.usecase.AuthUseCase;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;

import java.nio.charset.StandardCharsets;

/**
 * Rotas públicas abertas; {@code /api/admin/**} exige sessão (exceto login e logout). Sem CORS aqui: só o gateway
 * responde a navegador. Sem token CSRF: o cookie é {@code SameSite=Lax} por padrão e as rotas que mudam dados
 * usam POST/PUT/PATCH/DELETE.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final String UNAUTHORIZED_BODY =
            "{\"code\":\"UNAUTHORIZED\",\"message\":\"Faça login para continuar.\"}";

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http, AuthUseCase auth, CorsSettings corsSettings) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())
                // Perfil completo: o gateway faz o CORS. Standalone: o próprio booking (CorsSettings)
                .cors(cors -> {
                    if (corsSettings.enabled()) {
                        cors.configurationSource(corsSettings.source());
                    } else {
                        cors.disable();
                    }
                })
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/admin/login", "/api/admin/logout").permitAll()
                        .requestMatchers("/api/admin/**").authenticated()
                        .anyRequest().permitAll())
                .exceptionHandling(e -> e.authenticationEntryPoint((request, response, ex) -> {
                    response.setStatus(401);
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.setHeader("Cache-Control", "no-store");
                    response.getWriter().write(UNAUTHORIZED_BODY);
                }))
                .addFilterBefore(new SessionAuthenticationFilter(auth), AuthorizationFilter.class)
                .build();
    }
}
