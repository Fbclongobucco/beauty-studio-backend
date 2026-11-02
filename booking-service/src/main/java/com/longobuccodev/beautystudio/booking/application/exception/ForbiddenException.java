package com.longobuccodev.beautystudio.booking.application.exception;

import com.longobuccodev.beautystudio.booking.core.exception.CoreDomainException;

/** Ação exclusiva do master com sessão de profissional (403). */
public class ForbiddenException extends CoreDomainException {

    public ForbiddenException(String message) {
        super("FORBIDDEN", message);
    }
}
