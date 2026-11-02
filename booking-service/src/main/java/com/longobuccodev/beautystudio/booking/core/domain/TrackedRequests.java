package com.longobuccodev.beautystudio.booking.core.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Pedidos lembrados no aparelho da cliente (cookie {@code my_requests}): até {@link #MAX} ids, do mais recente para
 * o mais antigo, sem repetição. Só guarda ids, nunca dados da cliente.
 */
public record TrackedRequests(List<UUID> ids) {

    public static final int MAX = 10;
    public static final TrackedRequests EMPTY = new TrackedRequests(List.of());

    public TrackedRequests {
        ids = List.copyOf(ids.stream().distinct().limit(MAX).toList());
    }

    /** Põe o pedido no início; acima de {@link #MAX}, o mais antigo sai. */
    public TrackedRequests withFirst(UUID id) {
        List<UUID> out = new ArrayList<>();
        out.add(id);
        ids.stream().filter(i -> !i.equals(id)).forEach(out::add);
        return new TrackedRequests(out);
    }

    /** Mantém só os ids que ainda existem, na mesma ordem. */
    public TrackedRequests retainOnly(Collection<UUID> existing) {
        return new TrackedRequests(ids.stream().filter(existing::contains).toList());
    }

    public boolean isEmpty() {
        return ids.isEmpty();
    }

    /**
     * Pedido que ainda interessa à cliente: pendente; confirmado que não terminou; recusado ou cancelado com início
     * ainda no futuro (para ela ver o desfecho).
     */
    public static boolean isInProgress(Appointment a, Instant now) {
        return switch (a.status()) {
            case PENDING -> true;
            case CONFIRMED -> a.end().toInstant().isAfter(now);
            case REJECTED, CANCELLED -> a.start().toInstant().isAfter(now);
        };
    }
}
