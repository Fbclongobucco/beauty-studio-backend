package com.longobuccodev.beautystudio.booking.infra.security;

import com.longobuccodev.beautystudio.booking.application.port.LoginThrottle;
import com.longobuccodev.beautystudio.booking.infra.config.BookingProperties;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Janela deslizante em memória, por usuário e por origem. Com várias instâncias, o limite vale por instância
 * (risco aceito no design, D5).
 */
@Component
public class InMemoryLoginThrottle implements LoginThrottle {

    private final Map<String, Deque<Instant>> failures = new ConcurrentHashMap<>();
    private final int maxFailures;
    private final Duration window;
    private final Clock clock;

    public InMemoryLoginThrottle(BookingProperties properties, Clock clock) {
        this.maxFailures = properties.login().maxFailures();
        this.window = properties.login().window();
        this.clock = clock;
    }

    @Override
    public boolean isBlocked(String username, String origin) {
        return count("u:" + username) >= maxFailures || count("o:" + origin) >= maxFailures;
    }

    @Override
    public void recordFailure(String username, String origin) {
        add("u:" + username);
        add("o:" + origin);
    }

    @Override
    public void reset(String username, String origin) {
        failures.remove("u:" + username);
    }

    /** Esquece todas as falhas registradas. */
    public void clear() {
        failures.clear();
    }

    private void add(String key) {
        failures.compute(key, (k, q) -> {
            Deque<Instant> d = q == null ? new ArrayDeque<>() : q;
            prune(d);
            d.addLast(clock.instant());
            return d;
        });
    }

    private int count(String key) {
        Deque<Instant> d = failures.computeIfPresent(key, (k, q) -> {
            prune(q);
            return q.isEmpty() ? null : q;
        });
        return d == null ? 0 : d.size();
    }

    private void prune(Deque<Instant> d) {
        Instant limit = clock.instant().minus(window);
        while (!d.isEmpty() && !d.peekFirst().isAfter(limit)) {
            d.pollFirst();
        }
    }
}
