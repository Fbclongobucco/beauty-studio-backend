package com.longobuccodev.beautystudio.booking.core.exception;

/** Base das exceções de negócio. {@link #code()} é o código de erro do contrato HTTP do front. */
public abstract class CoreDomainException extends RuntimeException {

    private final String code;

    protected CoreDomainException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
