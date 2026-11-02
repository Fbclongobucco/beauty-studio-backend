package com.longobuccodev.beautystudio.booking.infra.security;

import com.longobuccodev.beautystudio.booking.core.domain.Account;
import com.longobuccodev.beautystudio.booking.core.security.SessionTokenIssuer;
import com.longobuccodev.beautystudio.booking.infra.config.BookingProperties;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** JWT HS256 com conta, perfil, profissional e versão da senha; validade de 12 h. */
@Component
public class JwtSessionTokenIssuer implements SessionTokenIssuer {

    static final String ISSUER = "beauty-studio-booking";
    static final String PASSWORD_VERSION = "pv";
    /** Audiência própria: o token do cookie my_requests (mesmo segredo) não vale como sessão, e vice-versa. */
    static final String AUDIENCE = "admin-session";

    private final NimbusJwtEncoder encoder;
    private final NimbusJwtDecoder decoder;
    private final Clock clock;

    public JwtSessionTokenIssuer(BookingProperties properties, Clock clock) {
        String secret = properties.security().jwtSecret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("APP_JWT_SECRET deve ter pelo menos 32 caracteres.");
        }
        SecretKey key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        this.decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps, new JwtIssuerValidator(ISSUER),
                new JwtClaimValidator<List<String>>("aud", aud -> aud != null && aud.contains(AUDIENCE))));
        this.clock = clock;
    }

    @Override
    public String issue(Account account) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(ISSUER)
                .audience(List.of(AUDIENCE))
                .subject(account.id().toString())
                .issuedAt(now)
                .expiresAt(now.plus(SESSION_TTL))
                .claim("role", account.role().value())
                .claim(PASSWORD_VERSION, account.passwordVersion())
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }

    @Override
    public Optional<SessionClaims> verify(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            Object version = jwt.getClaims().get(PASSWORD_VERSION);
            if (!(version instanceof Number n)) {
                return Optional.empty();
            }
            return Optional.of(new SessionClaims(UUID.fromString(jwt.getSubject()), n.intValue()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
