package com.longobuccodev.beautystudio.booking.infra.controllers;

import com.longobuccodev.beautystudio.booking.core.domain.PhotoType;
import com.longobuccodev.beautystudio.booking.core.exception.CoreDomainException;
import com.longobuccodev.beautystudio.booking.core.exception.DomainValidationException;
import com.longobuccodev.beautystudio.booking.infra.config.BookingProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.Map;

/** Exceções de negócio → código HTTP e corpo {@code { code, message, fields? }} em pt-BR. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final Map<String, HttpStatus> STATUS = Map.ofEntries(
            Map.entry("VALIDATION_ERROR", HttpStatus.BAD_REQUEST),
            Map.entry("NOT_FOUND", HttpStatus.NOT_FOUND),
            Map.entry("FORBIDDEN", HttpStatus.FORBIDDEN),
            Map.entry("UNAUTHORIZED", HttpStatus.UNAUTHORIZED),
            Map.entry("LOGIN_DISABLED", HttpStatus.SERVICE_UNAVAILABLE),
            Map.entry("TOO_MANY_ATTEMPTS", HttpStatus.TOO_MANY_REQUESTS),
            Map.entry("SLOT_UNAVAILABLE", HttpStatus.CONFLICT),
            Map.entry("SLOT_TAKEN", HttpStatus.CONFLICT),
            Map.entry("INVALID_TRANSITION", HttpStatus.CONFLICT),
            Map.entry("SERVICE_UNAVAILABLE", HttpStatus.SERVICE_UNAVAILABLE));

    private final long maxPhotoBytes;

    public GlobalExceptionHandler(BookingProperties properties) {
        this.maxPhotoBytes = properties.storage().maxPhotoSize().toBytes();
    }

    @ExceptionHandler(DomainValidationException.class)
    ResponseEntity<ErrorBody> validation(DomainValidationException e) {
        return ResponseEntity.badRequest().body(new ErrorBody(e.code(), e.getMessage(), e.fields()));
    }

    @ExceptionHandler(CoreDomainException.class)
    ResponseEntity<ErrorBody> domain(CoreDomainException e) {
        HttpStatus status = STATUS.getOrDefault(e.code(), HttpStatus.BAD_REQUEST);
        if (status == HttpStatus.SERVICE_UNAVAILABLE && e.getCause() != null) {
            log.warn("Dependencia indisponivel: {}", e.getCause().toString());
        }
        return ResponseEntity.status(status).body(new ErrorBody(e.code(), e.getMessage(), null));
    }

    /** Corpo que não é JSON: tratado como corpo vazio, com os campos obrigatórios apontados. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorBody> unreadable(HttpMessageNotReadableException e) {
        return ResponseEntity.badRequest().body(new ErrorBody("VALIDATION_ERROR", "Confira os campos destacados.",
                Map.of()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ErrorBody> tooLarge(MaxUploadSizeExceededException e) {
        return ResponseEntity.badRequest().body(new ErrorBody("VALIDATION_ERROR", "Confira os campos destacados.",
                Map.of("photo", PhotoType.sizeMessage(maxPhotoBytes))));
    }

    @ExceptionHandler(MultipartException.class)
    ResponseEntity<ErrorBody> badMultipart(MultipartException e) {
        return ResponseEntity.badRequest().body(new ErrorBody("VALIDATION_ERROR", "Confira os campos destacados.",
                Map.of("photo", PhotoType.MISSING_MESSAGE)));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ErrorBody> noRoute(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorBody("NOT_FOUND", "Não encontrado.", null));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ErrorBody> method(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(new ErrorBody("NOT_FOUND", "Operação não suportada.", null));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorBody> unexpected(Exception e) {
        log.error("Erro inesperado", e);
        return ResponseEntity.internalServerError()
                .body(new ErrorBody("INTERNAL_ERROR", "Algo deu errado. Tente de novo.", null));
    }
}
