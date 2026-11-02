package com.longobuccodev.beautystudio.gateway;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Resposta do circuit breaker: mesmo formato de erro que o front ja trata ({@code {code, message}}). */
@RestController
public class FallbackController {

    static final String PATH = "/fallback/service-unavailable";

    record ErrorBody(String code, String message) {
    }

    @RequestMapping(PATH)
    ResponseEntity<ErrorBody> serviceUnavailable() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Cache-Control", "no-store")
                .body(new ErrorBody("SERVICE_UNAVAILABLE",
                        "Serviço temporariamente indisponível. Tente de novo em instantes."));
    }
}
