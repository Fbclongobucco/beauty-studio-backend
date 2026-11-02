package com.longobuccodev.beautystudio.booking.application.exception;

import com.longobuccodev.beautystudio.booking.core.exception.CoreDomainException;

/** Sem sessão válida ou usuário e senha incorretos (401). */
public class UnauthorizedException extends CoreDomainException {

    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message);
    }
}
