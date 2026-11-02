package com.longobuccodev.beautystudio.booking.infra.controllers;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

/** Formato de erro do contrato do front: {@code { code, message, fields? }}. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorBody(String code, String message, Map<String, String> fields) {
}
