package com.longobuccodev.beautystudio.booking.infra.controllers;

import com.longobuccodev.beautystudio.booking.application.dto.Dtos.AppointmentDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.AppointmentsResponse;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.MarkedDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.PendingCountDto;
import com.longobuccodev.beautystudio.booking.application.security.AdminPrincipal;
import com.longobuccodev.beautystudio.booking.application.usecase.AdminAppointmentUseCase;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin/appointments")
public class AdminAppointmentController {

    private final AdminAppointmentUseCase appointments;

    public AdminAppointmentController(AdminAppointmentUseCase appointments) {
        this.appointments = appointments;
    }

    @GetMapping
    AppointmentsResponse list(@AuthenticationPrincipal AdminPrincipal principal,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) String date,
                              @RequestParam(required = false) String from,
                              @RequestParam(required = false) String to,
                              @RequestParam(required = false) String professionalId) {
        return appointments.list(principal, status, date, from, to, professionalId);
    }

    @GetMapping("/pending-count")
    PendingCountDto pendingCount(@AuthenticationPrincipal AdminPrincipal principal) {
        return appointments.pendingCount(principal);
    }

    @PostMapping("/mark-seen")
    MarkedDto markSeen(@AuthenticationPrincipal AdminPrincipal principal,
                       @RequestBody(required = false) Map<String, Object> body) {
        return appointments.markSeen(principal, body);
    }

    @PostMapping("/{id}/{action}")
    AppointmentDto act(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable String id,
                       @PathVariable String action, @RequestBody(required = false) Map<String, Object> body) {
        return appointments.transition(principal, id, action, body);
    }
}
