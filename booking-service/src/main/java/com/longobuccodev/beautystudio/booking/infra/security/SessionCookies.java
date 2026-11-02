package com.longobuccodev.beautystudio.booking.infra.security;

import com.longobuccodev.beautystudio.booking.core.security.SessionTokenIssuer;
import com.longobuccodev.beautystudio.booking.infra.config.BookingProperties;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Cookie {@code admin_session}: o mesmo nome que o {@code proxy.ts} do front confere. */
@Component
public class SessionCookies {

    public static final String NAME = "admin_session";

    private final BookingProperties.Security config;

    public SessionCookies(BookingProperties properties) {
        this.config = properties.security();
    }

    public ResponseCookie create(String token) {
        return base(token).maxAge(SessionTokenIssuer.SESSION_TTL).build();
    }

    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(NAME, value)
                .httpOnly(true)
                .secure(config.cookieSecure())
                .sameSite(config.cookieSameSite())
                .path("/");
    }

    public static String read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie c : cookies) {
            if (NAME.equals(c.getName())) {
                return c.getValue();
            }
        }
        return null;
    }
}
