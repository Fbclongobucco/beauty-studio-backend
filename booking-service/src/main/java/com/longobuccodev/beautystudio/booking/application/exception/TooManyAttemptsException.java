package com.longobuccodev.beautystudio.booking.application.exception;

import com.longobuccodev.beautystudio.booking.core.exception.CoreDomainException;

/** Tentativas de login acima do limite na janela (429). */
public class TooManyAttemptsException extends CoreDomainException {

    public TooManyAttemptsException(String message) {
        super("TOO_MANY_ATTEMPTS", message);
    }
}
