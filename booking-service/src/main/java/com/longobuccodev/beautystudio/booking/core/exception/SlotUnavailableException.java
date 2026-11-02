package com.longobuccodev.beautystudio.booking.core.exception;

/** Horário fora do expediente, da janela ou ocupado por confirmado no envio do pedido (409). */
public class SlotUnavailableException extends CoreDomainException {

    public SlotUnavailableException() {
        super("SLOT_UNAVAILABLE", "Esse horário acabou de ser ocupado. Escolha outro.");
    }
}
