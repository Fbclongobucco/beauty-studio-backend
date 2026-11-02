package com.longobuccodev.beautystudio.booking.application.usecase;

import com.longobuccodev.beautystudio.booking.application.dto.Dtos.PublicAppointmentDto;
import com.longobuccodev.beautystudio.booking.application.mapper.DtoMapper;
import com.longobuccodev.beautystudio.booking.core.domain.Appointment;
import com.longobuccodev.beautystudio.booking.core.domain.TrackedRequests;
import com.longobuccodev.beautystudio.booking.core.repository.AppointmentRepository;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Pedidos em andamento lembrados no aparelho da cliente, sem login. */
public class TrackedRequestsUseCase {

    /**
     * @param appointments pedidos em andamento, do início mais próximo para o mais distante
     * @param remaining    ids do aparelho que ainda existem (os inexistentes saem do cookie)
     */
    public record Result(List<PublicAppointmentDto> appointments, TrackedRequests remaining) {
    }

    private final AppointmentRepository repository;
    private final Clock clock;

    public TrackedRequestsUseCase(AppointmentRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Result inProgress(TrackedRequests tracked) {
        Instant now = clock.instant();
        List<Appointment> existing = tracked.ids().stream().map(repository::findById).flatMap(Optional::stream)
                .toList();
        List<PublicAppointmentDto> list = existing.stream()
                .filter(a -> TrackedRequests.isInProgress(a, now))
                .sorted(Comparator.comparing(Appointment::start))
                .map(DtoMapper::toPublicDto)
                .toList();
        return new Result(list, tracked.retainOnly(existing.stream().map(Appointment::id).toList()));
    }

    /** Id inválido conta como ausente. */
    public static Optional<UUID> parseId(String id) {
        return BookingUseCase.parseId(id);
    }
}
