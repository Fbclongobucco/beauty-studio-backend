package com.longobuccodev.beautystudio.messaging.infra;

import com.longobuccodev.beautystudio.messaging.core.AppointmentEvent;
import com.longobuccodev.beautystudio.messaging.core.InvalidEventException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/** Converte o evento de e para JSON, sem cabecalho de tipo Java: o contrato e o JSON. */
public class AppointmentEventCodec {

    private final JsonMapper mapper;

    public AppointmentEventCodec(JsonMapper mapper) {
        // Datas como ISO 8601 mantendo o deslocamento enviado (-03:00), sem converter para UTC na leitura
        this.mapper = mapper.rebuild()
                .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
                .disable(DateTimeFeature.ADJUST_DATES_TO_CONTEXT_TIME_ZONE)
                .build();
    }

    public byte[] encode(AppointmentEvent event) {
        return mapper.writeValueAsBytes(event);
    }

    public AppointmentEvent decode(byte[] body) {
        try {
            return mapper.readValue(body, AppointmentEvent.class);
        } catch (JacksonException e) {
            throw new InvalidEventException("JSON invalido: " + e.getOriginalMessage());
        }
    }
}
