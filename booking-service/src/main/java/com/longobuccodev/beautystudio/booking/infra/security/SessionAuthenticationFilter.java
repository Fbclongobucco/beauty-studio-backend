package com.longobuccodev.beautystudio.booking.infra.security;

import com.longobuccodev.beautystudio.booking.application.security.AdminPrincipal;
import com.longobuccodev.beautystudio.booking.application.usecase.AuthUseCase;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/** Lê o cookie de sessão e, se válido para a versão atual da senha, autentica a requisição. */
public class SessionAuthenticationFilter extends OncePerRequestFilter {

    private final AuthUseCase auth;

    public SessionAuthenticationFilter(AuthUseCase auth) {
        this.auth = auth;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String token = SessionCookies.read(request);
        if (token != null) {
            auth.authenticate(token).ifPresent(principal -> SecurityContextHolder.getContext().setAuthentication(
                    UsernamePasswordAuthenticationToken.authenticated(principal, null, authorities(principal))));
        }
        chain.doFilter(request, response);
    }

    private static List<SimpleGrantedAuthority> authorities(AdminPrincipal principal) {
        return List.of(new SimpleGrantedAuthority("ROLE_" + principal.role().name()));
    }
}
