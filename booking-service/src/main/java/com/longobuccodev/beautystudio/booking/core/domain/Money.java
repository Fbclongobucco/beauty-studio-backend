package com.longobuccodev.beautystudio.booking.core.domain;

/** Preço em centavos. Nulo significa "Consultar valor". */
public final class Money {

    public static final String MESSAGE = "Informe um valor em reais, ex.: 45,00";

    private Money() {
    }

    /** Mensagem de erro, ou nulo se o valor (centavos inteiros não negativos, ou nulo) for válido. */
    public static String validateCents(Object value) {
        if (value == null) {
            return null;
        }
        Long cents = Numbers.asLong(value);
        return cents == null || cents < 0 || cents > Integer.MAX_VALUE ? MESSAGE : null;
    }
}
