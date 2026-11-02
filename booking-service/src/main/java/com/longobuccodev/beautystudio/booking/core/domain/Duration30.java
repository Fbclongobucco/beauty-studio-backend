package com.longobuccodev.beautystudio.booking.core.domain;

/** Duração de serviço: minutos inteiros, múltiplo de 30, de 30 min a 8 h. */
public final class Duration30 {

    private Duration30() {
    }

    /** Mensagem de erro, ou nulo se válida. */
    public static String validate(Object value) {
        Long min = Numbers.asLong(value);
        if (min == null) {
            return "Informe a duração em minutos.";
        }
        if (min % Schedule.STEP_MIN != 0) {
            return "A duração deve ser múltiplo de 30 minutos.";
        }
        if (min < Schedule.MIN_DURATION || min > Schedule.MAX_DURATION) {
            return "A duração deve ficar entre 30 minutos e 8 horas.";
        }
        return null;
    }
}
