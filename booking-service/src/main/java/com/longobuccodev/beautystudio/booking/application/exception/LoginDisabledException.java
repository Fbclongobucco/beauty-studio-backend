package com.longobuccodev.beautystudio.booking.application.exception;

import com.longobuccodev.beautystudio.booking.core.exception.CoreDomainException;

/** Nenhuma conta ativa configurada (503). */
public class LoginDisabledException extends CoreDomainException {

    public LoginDisabledException(String message) {
        super("LOGIN_DISABLED", message);
    }
}
