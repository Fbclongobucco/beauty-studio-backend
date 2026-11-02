package com.longobuccodev.beautystudio.booking.infra.controllers;

import com.longobuccodev.beautystudio.booking.application.dto.Dtos.AccountDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.AccountsResponse;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.EarningsDto;
import com.longobuccodev.beautystudio.booking.application.security.AdminPrincipal;
import com.longobuccodev.beautystudio.booking.application.usecase.AccountUseCase;
import com.longobuccodev.beautystudio.booking.application.usecase.EarningsUseCase;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** Logins das profissionais (só master) e ganhos do período. */
@RestController
public class AdminAccountController {

    private final AccountUseCase accounts;
    private final EarningsUseCase earnings;

    public AdminAccountController(AccountUseCase accounts, EarningsUseCase earnings) {
        this.accounts = accounts;
        this.earnings = earnings;
    }

    @GetMapping("/api/admin/accounts")
    AccountsResponse accounts(@AuthenticationPrincipal AdminPrincipal principal) {
        return accounts.list(principal);
    }

    @PostMapping("/api/admin/accounts")
    @ResponseStatus(HttpStatus.CREATED)
    AccountDto setAccount(@AuthenticationPrincipal AdminPrincipal principal,
                          @RequestBody(required = false) Map<String, Object> body) {
        return accounts.setProfessionalPassword(principal, body);
    }

    @GetMapping("/api/admin/earnings")
    EarningsDto earnings(@AuthenticationPrincipal AdminPrincipal principal,
                         @RequestParam(required = false) String from, @RequestParam(required = false) String to,
                         @RequestParam(required = false) String professionalId) {
        return earnings.earnings(principal, from, to, professionalId);
    }
}
