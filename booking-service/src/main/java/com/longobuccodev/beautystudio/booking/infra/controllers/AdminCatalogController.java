package com.longobuccodev.beautystudio.booking.infra.controllers;

import com.longobuccodev.beautystudio.booking.application.dto.Dtos.AdminCatalogResponse;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.CategoriesResponse;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.CategoryDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.ProfessionalDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.ProfessionalsResponse;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.ServiceDto;
import com.longobuccodev.beautystudio.booking.application.security.AdminPrincipal;
import com.longobuccodev.beautystudio.booking.application.usecase.CatalogUseCase;
import com.longobuccodev.beautystudio.booking.application.usecase.ProfessionalUseCase;
import com.longobuccodev.beautystudio.booking.application.usecase.ServicePhotoUseCase;
import com.longobuccodev.beautystudio.booking.core.domain.PhotoType;
import com.longobuccodev.beautystudio.booking.core.exception.DomainValidationException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.util.WebUtils;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/** Catálogo no painel: serviços (com foto), categorias e profissionais. */
@RestController
public class AdminCatalogController {

    /** Folga para os cabeçalhos do multipart além do arquivo, como na API de teste. */
    static final long MULTIPART_OVERHEAD = 64 * 1024;

    private final CatalogUseCase catalog;
    private final ProfessionalUseCase professionals;
    private final ServicePhotoUseCase photos;

    public AdminCatalogController(CatalogUseCase catalog, ProfessionalUseCase professionals,
                                  ServicePhotoUseCase photos) {
        this.catalog = catalog;
        this.professionals = professionals;
        this.photos = photos;
    }

    @GetMapping("/api/admin/services")
    AdminCatalogResponse services(@AuthenticationPrincipal AdminPrincipal principal) {
        return catalog.adminCatalog(principal);
    }

    @PostMapping("/api/admin/services")
    @ResponseStatus(HttpStatus.CREATED)
    ServiceDto createService(@AuthenticationPrincipal AdminPrincipal principal,
                             @RequestBody(required = false) Map<String, Object> body) {
        return catalog.createService(principal, body);
    }

    @PatchMapping("/api/admin/services/{id}")
    ServiceDto updateService(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable String id,
                             @RequestBody(required = false) Map<String, Object> body) {
        return catalog.updateService(principal, id, body);
    }

    /**
     * Foto no campo multipart {@code photo}. Escopo conferido antes de ler o arquivo; corpo declarado acima do
     * limite é recusado sem ser carregado.
     */
    @PutMapping("/api/admin/services/{id}/photo")
    ServiceDto setPhoto(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable String id,
                        HttpServletRequest request) {
        photos.requireAccess(principal, id);
        if (request.getContentLengthLong() > photos.maxBytes() + MULTIPART_OVERHEAD) {
            throw DomainValidationException.field("photo", PhotoType.sizeMessage(photos.maxBytes()));
        }
        byte[] content = null;
        // O request pode chegar embrulhado (Spring Security): busca o multipart por baixo dos wrappers
        MultipartHttpServletRequest multipart = WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
        if (multipart != null) {
            MultipartFile file = multipart.getFile("photo");
            if (file != null) {
                try {
                    content = file.getBytes();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            }
        }
        return photos.setPhoto(principal, id, content);
    }

    @DeleteMapping("/api/admin/services/{id}/photo")
    ServiceDto removePhoto(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable String id) {
        return photos.removePhoto(principal, id);
    }

    @GetMapping("/api/admin/categories")
    CategoriesResponse categories() {
        return catalog.listCategories();
    }

    @PostMapping("/api/admin/categories")
    @ResponseStatus(HttpStatus.CREATED)
    CategoryDto createCategory(@AuthenticationPrincipal AdminPrincipal principal,
                               @RequestBody(required = false) Map<String, Object> body) {
        return catalog.createCategory(principal, body);
    }

    @PatchMapping("/api/admin/categories/{id}")
    CategoryDto updateCategory(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable String id,
                               @RequestBody(required = false) Map<String, Object> body) {
        return catalog.updateCategory(principal, id, body);
    }

    @GetMapping("/api/admin/professionals")
    ProfessionalsResponse professionals(@AuthenticationPrincipal AdminPrincipal principal) {
        return professionals.list(principal);
    }

    @PostMapping("/api/admin/professionals")
    @ResponseStatus(HttpStatus.CREATED)
    ProfessionalDto createProfessional(@AuthenticationPrincipal AdminPrincipal principal,
                                       @RequestBody(required = false) Map<String, Object> body) {
        return professionals.create(principal, body);
    }

    @PatchMapping("/api/admin/professionals/{id}")
    ProfessionalDto updateProfessional(@AuthenticationPrincipal AdminPrincipal principal, @PathVariable String id,
                                       @RequestBody(required = false) Map<String, Object> body) {
        return professionals.update(principal, id, body);
    }
}
