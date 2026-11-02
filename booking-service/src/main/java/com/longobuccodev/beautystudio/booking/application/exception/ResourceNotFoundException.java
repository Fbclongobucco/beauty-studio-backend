package com.longobuccodev.beautystudio.booking.application.exception;

import com.longobuccodev.beautystudio.booking.core.exception.CoreDomainException;

/** Recurso inexistente ou de outra profissional (404, sem revelar que existe). */
public class ResourceNotFoundException extends CoreDomainException {

    public ResourceNotFoundException(String message) {
        super("NOT_FOUND", message);
    }
}
