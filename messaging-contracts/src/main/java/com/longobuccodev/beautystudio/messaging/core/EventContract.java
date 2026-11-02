package com.longobuccodev.beautystudio.messaging.core;

/** Constantes do contrato e da topologia, compartilhadas por publicador e consumidores. */
public final class EventContract {

    /** Versao do contrato. Mudanca incompativel nos records do evento MUST incrementar este numero. */
    public static final int CONTRACT_VERSION = 1;

    /** Exchange topic duravel dos eventos de agendamento; a routing key e o tipo do evento. */
    public static final String APPOINTMENTS_EXCHANGE = "beauty.appointments";

    /** Exchange de mensagens mortas das filas consumidoras. */
    public static final String DEAD_LETTER_EXCHANGE = "beauty.appointments.dlx";

    /** Cabecalho AMQP com a versao do contrato. */
    public static final String VERSION_HEADER = "x-event-version";

    /** Cabecalho AMQP com o numero de tentativas ja feitas pelo consumidor. */
    public static final String ATTEMPTS_HEADER = "x-attempts";

    /** Todas as routing keys de agendamento. */
    public static final String ALL_APPOINTMENT_EVENTS = "appointment.*";

    private EventContract() {
    }
}
