package com.longobuccodev.beautystudio.booking.core.exception;

/** Armazenamento de fotos fora do ar ou com o circuito aberto (503). */
public class StorageUnavailableException extends CoreDomainException {

    public StorageUnavailableException(Throwable cause) {
        super("SERVICE_UNAVAILABLE", "Serviço temporariamente indisponível. Tente de novo em instantes.");
        initCause(cause);
    }
}
