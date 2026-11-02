package com.longobuccodev.beautystudio.booking.core.exception;

/** Confirmação de pedido cujo intervalo já tem outro confirmado da mesma profissional (409). */
public class SlotTakenException extends CoreDomainException {

    public SlotTakenException() {
        super("SLOT_TAKEN", "Horário já ocupado por outro agendamento confirmado");
    }
}
