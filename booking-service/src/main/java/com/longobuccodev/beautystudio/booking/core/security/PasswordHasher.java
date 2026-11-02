package com.longobuccodev.beautystudio.booking.core.security;

/** Hash adaptativo com sal. A senha em texto nunca é guardada nem registrada em log. */
public interface PasswordHasher {

    String hash(String rawPassword);

    boolean matches(String rawPassword, String hash);
}
