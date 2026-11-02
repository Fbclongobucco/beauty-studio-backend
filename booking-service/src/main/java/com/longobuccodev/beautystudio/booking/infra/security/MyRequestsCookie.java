package com.longobuccodev.beautystudio.booking.infra.security;

import com.longobuccodev.beautystudio.booking.core.domain.TrackedRequests;
import com.longobuccodev.beautystudio.booking.infra.config.BookingProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Cookie {@code my_requests}: pedidos feitos ou abertos neste aparelho, num JWT HS256 assinado pelo servidor
 * ({@code aud: my-requests}, que não vale como sessão do painel). Só ids; nenhum dado da cliente.
 * Valor inválido, expirado ou adulterado conta como vazio.
 */
@Component
public class MyRequestsCookie {

    public static final String NAME = "my_requests";
    static final String PATH = "/api/appointments";
    static final String AUDIENCE = "my-requests";
    static final Duration TTL = Duration.ofDays(60);

    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final BookingProperties.Security config;
    private final Clock clock;

    public MyRequestsCookie(BookingProperties properties, Clock clock) {
        this.config = properties.security();
        SecretKey key = new SecretKeySpec(config.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps,
                new JwtIssuerValidator(JwtSessionTokenIssuer.ISSUER),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(AUDIENCE))));
        this.clock = clock;
    }

    public TrackedRequests read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return TrackedRequests.EMPTY;
        }
        for (Cookie c : cookies) {
            if (NAME.equals(c.getName())) {
                return decode(c.getValue());
            }
        }
        return TrackedRequests.EMPTY;
    }

    TrackedRequests decode(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            Object ids = jwt.getClaims().get("ids");
            if (!(ids instanceof List<?> list)) {
                return TrackedRequests.EMPTY;
            }
            return new TrackedRequests(list.stream().map(String::valueOf).map(UUID::fromString).toList());
        } catch (JwtException | IllegalArgumentException e) {
            return TrackedRequests.EMPTY;
        }
    }

    /** Grava a lista (renovando a validade) ou, vazia, apaga o cookie. */
    public ResponseCookie write(TrackedRequests tracked) {
        if (tracked.isEmpty()) {
            return base("").maxAge(Duration.ZERO).build();
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(JwtSessionTokenIssuer.ISSUER)
                .audience(List.of(AUDIENCE))
                .issuedAt(now)
                .expiresAt(now.plus(TTL))
                .claim("ids", tracked.ids().stream().map(UUID::toString).toList())
                .build();
        String token = encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
        return base(token).maxAge(TTL).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(config.cookieSecure())
                .sameSite(config.cookieSameSite())
                .path(PATH);
    }
}
