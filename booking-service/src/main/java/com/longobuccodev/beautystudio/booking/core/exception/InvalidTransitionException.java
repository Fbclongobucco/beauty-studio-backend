package com.longobuccodev.beautystudio.booking.core.exception;

/** Ação não permitida no status atual (409). */
public class InvalidTransitionException extends CoreDomainException {

    public InvalidTransitionException() {
        super("INVALID_TRANSITION", "Essa ação não é possível no status atual.");
    }
}
