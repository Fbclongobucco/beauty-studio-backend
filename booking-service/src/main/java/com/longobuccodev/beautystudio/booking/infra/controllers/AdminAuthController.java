package com.longobuccodev.beautystudio.booking.infra.controllers;

import com.longobuccodev.beautystudio.booking.application.dto.Dtos.OkDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.SessionDto;
import com.longobuccodev.beautystudio.booking.application.security.AdminPrincipal;
import com.longobuccodev.beautystudio.booking.application.usecase.AuthUseCase;
import com.longobuccodev.beautystudio.booking.application.usecase.ProfessionalUseCase;
import com.longobuccodev.beautystudio.booking.infra.config.BookingProperties;
import com.longobuccodev.beautystudio.booking.infra.security.SessionCookies;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Login, logout e dados da sessão. {@code PATCH /api/admin/me}: a profissional informa o próprio WhatsApp. */
@RestController
public class AdminAuthController {

    private final AuthUseCase auth;
    private final ProfessionalUseCase professionals;
    private final SessionCookies cookies;
    private final int trustedProxyHops;

    public AdminAuthController(AuthUseCase auth, ProfessionalUseCase professionals, SessionCookies cookies,
                               BookingProperties properties) {
        this.auth = auth;
        this.professionals = professionals;
        this.cookies = cookies;
        this.trustedProxyHops = Math.max(1, properties.login().trustedProxyHops());
    }

    @PostMapping("/api/admin/login")
    ResponseEntity<OkDto> login(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request) {
        Map<String, Object> b = body == null ? Map.of() : body;
        String token = auth.login(b.get("username") instanceof String u ? u : "",
                b.get("password") instanceof String p ? p : "", clientOrigin(request, trustedProxyHops));
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookies.create(token).toString()).body(new OkDto(true));
    }

    @PostMapping("/api/admin/logout")
    ResponseEntity<OkDto> logout() {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE, cookies.clear().toString()).body(new OkDto(true));
    }

    @GetMapping("/api/admin/me")
    SessionDto me(@AuthenticationPrincipal AdminPrincipal principal) {
        return professionals.me(principal);
    }

    @PatchMapping("/api/admin/me")
    SessionDto updateMe(@AuthenticationPrincipal AdminPrincipal principal,
                        @RequestBody(required = false) Map<String, Object> body) {
        return professionals.updateMyWhatsapp(principal, body);
    }

    /**
     * IP de origem para o limite de tentativas: a entrada do X-Forwarded-For acrescentada pelo proxy confiável mais
     * distante ({@code hops} a partir do fim). Entradas anteriores vêm do cliente e podem ser forjadas.
     */
    static String clientOrigin(HttpServletRequest request, int hops) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            String[] entries = forwarded.split(",");
            return entries[Math.max(0, entries.length - hops)].strip();
        }
        return request.getRemoteAddr();
    }
}
