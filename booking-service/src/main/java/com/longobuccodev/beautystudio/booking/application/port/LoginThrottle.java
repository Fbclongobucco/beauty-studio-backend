package com.longobuccodev.beautystudio.booking.application.port;

/** Limite de tentativas de login falhas por usuário e por origem, numa janela de tempo. */
public interface LoginThrottle {

    boolean isBlocked(String username, String origin);

    void recordFailure(String username, String origin);

    void reset(String username, String origin);
}
