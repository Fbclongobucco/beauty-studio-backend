package com.longobuccodev.beautystudio.booking.infra.controllers;

import com.longobuccodev.beautystudio.booking.application.dto.Dtos.AppointmentDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.AvailabilityResponse;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.DaysResponse;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.PublicAppointmentDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.ServicesResponse;
import com.longobuccodev.beautystudio.booking.application.usecase.AvailabilityUseCase;
import com.longobuccodev.beautystudio.booking.application.usecase.BookingUseCase;
import com.longobuccodev.beautystudio.booking.application.usecase.CatalogUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Endpoints públicos (sem login), mesmos caminhos da API de teste do front. */
@RestController
public class PublicController {

    private final CatalogUseCase catalog;
    private final AvailabilityUseCase availability;
    private final BookingUseCase booking;

    public PublicController(CatalogUseCase catalog, AvailabilityUseCase availability, BookingUseCase booking) {
        this.catalog = catalog;
        this.availability = availability;
        this.booking = booking;
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
    AppointmentDto create(@RequestBody(required = false) Map<String, Object> body) {
        return booking.create(body);
    }

    @GetMapping("/api/appointments/{id}")
    PublicAppointmentDto status(@PathVariable String id) {
        return booking.publicStatus(id);
    }
}
