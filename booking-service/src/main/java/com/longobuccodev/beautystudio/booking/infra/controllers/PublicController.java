package com.longobuccodev.beautystudio.booking.infra.controllers;

import com.longobuccodev.beautystudio.booking.application.dto.Dtos.AppointmentDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.AvailabilityResponse;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.DaysResponse;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.PublicAppointmentDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.ServicesResponse;
import com.longobuccodev.beautystudio.booking.application.usecase.AvailabilityUseCase;
import com.longobuccodev.beautystudio.booking.application.usecase.BookingUseCase;
import com.longobuccodev.beautystudio.booking.application.usecase.CatalogUseCase;
import com.longobuccodev.beautystudio.booking.application.usecase.TrackedRequestsUseCase;
import com.longobuccodev.beautystudio.booking.core.domain.TrackedRequests;
import com.longobuccodev.beautystudio.booking.infra.security.MyRequestsCookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Endpoints públicos (sem login), mesmos caminhos da API de teste do front. */
@RestController
public class PublicController {

    private final CatalogUseCase catalog;
    private final AvailabilityUseCase availability;
    private final BookingUseCase booking;
    private final TrackedRequestsUseCase tracked;
    private final MyRequestsCookie myRequests;

    public PublicController(CatalogUseCase catalog, AvailabilityUseCase availability, BookingUseCase booking,
                            TrackedRequestsUseCase tracked, MyRequestsCookie myRequests) {
        this.catalog = catalog;
        this.availability = availability;
        this.booking = booking;
        this.tracked = tracked;
        this.myRequests = myRequests;
    }

    /** Pedidos em andamento deste aparelho (cookie my_requests). */
    public record MyRequestsResponse(List<PublicAppointmentDto> appointments) {
    }

    @GetMapping("/api/services")
    ServicesResponse services() {
        return catalog.publicCatalog();
    }

    @GetMapping("/api/availability")
    AvailabilityResponse availability(@RequestParam(required = false) String serviceId,
                                      @RequestParam(required = false) String date) {
        return availability.slots(serviceId, date);
    }

    @GetMapping("/api/availability/days")
    DaysResponse days(@RequestParam(required = false) String serviceId, @RequestParam(required = false) String from,
                      @RequestParam(required = false) String days) {
        return availability.days(serviceId, from, days);
    }

    @PostMapping("/api/appointments")
    @ResponseStatus(HttpStatus.CREATED)
    AppointmentDto create(@RequestBody(required = false) Map<String, Object> body, HttpServletRequest request,
                          HttpServletResponse response) {
        AppointmentDto created = booking.create(body);
        remember(request, response, UUID.fromString(created.id()));
        return created;
    }

    // Rota literal: tem prioridade sobre /api/appointments/{id}
    @GetMapping("/api/appointments/mine")
    MyRequestsResponse mine(HttpServletRequest request, HttpServletResponse response) {
        return respond(myRequests.read(request), response);
    }

    @GetMapping("/api/appointments/{id}")
    PublicAppointmentDto status(@PathVariable String id, HttpServletRequest request, HttpServletResponse response) {
        PublicAppointmentDto dto = booking.publicStatus(id);
        // Abrir o link do pedido (inclusive o do WhatsApp, em outro aparelho) lembra o pedido neste aparelho
        remember(request, response, UUID.fromString(dto.id()));
        return dto;
    }

    private void remember(HttpServletRequest request, HttpServletResponse response, UUID id) {
        response.addHeader(HttpHeaders.SET_COOKIE, myRequests.write(myRequests.read(request).withFirst(id)).toString());
    }

    /** Lista em andamento; ids que não existem mais saem do cookie. */
    private MyRequestsResponse respond(TrackedRequests current, HttpServletResponse response) {
        TrackedRequestsUseCase.Result result = tracked.inProgress(current);
        if (!result.remaining().equals(current) && !response.containsHeader(HttpHeaders.SET_COOKIE)) {
            response.addHeader(HttpHeaders.SET_COOKIE, myRequests.write(result.remaining()).toString());
        }
        return new MyRequestsResponse(result.appointments());
    }
}
