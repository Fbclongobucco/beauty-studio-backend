package com.longobuccodev.beautystudio.booking.application.usecase;

import com.longobuccodev.beautystudio.booking.application.dto.Dtos.AdminCatalogResponse;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.CategoriesResponse;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.CategoryDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.ProfessionalRef;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.PublicCategoryDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.PublicServiceDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.ServiceDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.ServicesResponse;
import com.longobuccodev.beautystudio.booking.application.exception.ForbiddenException;
import com.longobuccodev.beautystudio.booking.application.exception.ResourceNotFoundException;
import com.longobuccodev.beautystudio.booking.application.mapper.DtoMapper;
import com.longobuccodev.beautystudio.booking.application.port.TransactionRunner;
import com.longobuccodev.beautystudio.booking.application.security.AccessScope;
import com.longobuccodev.beautystudio.booking.application.security.AdminPrincipal;
import com.longobuccodev.beautystudio.booking.application.validation.InputValidators;
import com.longobuccodev.beautystudio.booking.application.validation.InputValidators.ServiceInput;
import com.longobuccodev.beautystudio.booking.core.domain.Category;
import com.longobuccodev.beautystudio.booking.core.domain.Professional;
import com.longobuccodev.beautystudio.booking.core.domain.SalonService;
import com.longobuccodev.beautystudio.booking.core.domain.Slugs;
import com.longobuccodev.beautystudio.booking.core.exception.DomainValidationException;
import com.longobuccodev.beautystudio.booking.core.repository.CategoryRepository;
import com.longobuccodev.beautystudio.booking.core.repository.ProfessionalRepository;
import com.longobuccodev.beautystudio.booking.core.repository.ServiceRepository;
import com.longobuccodev.beautystudio.booking.core.storage.PhotoStorage;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Catálogo público e manutenção de serviços e categorias pelo painel (spec service-catalog do front). */
public class CatalogUseCase {

    static final String SERVICE_NOT_FOUND = "Serviço não encontrado.";
    static final String CATEGORY_NAME = "Informe o nome da categoria (2 a 40 caracteres).";
    static final String CATEGORY_NOTE = "Use no máximo 200 caracteres.";

    private final ServiceRepository services;
    private final CategoryRepository categories;
    private final ProfessionalRepository professionals;
    private final PhotoStorage storage;
    private final TransactionRunner tx;

    public CatalogUseCase(ServiceRepository services, CategoryRepository categories,
                          ProfessionalRepository professionals, PhotoStorage storage, TransactionRunner tx) {
        this.services = services;
        this.categories = categories;
        this.professionals = professionals;
        this.storage = storage;
        this.tx = tx;
    }

    /** Categorias com serviços ativos, sem a marca de duração estimada nem o WhatsApp das profissionais. */
    public ServicesResponse publicCatalog() {
        Map<String, String> names = professionals.findAll().stream()
                .collect(Collectors.toMap(Professional::id, Professional::name));
        List<SalonService> all = services.findAll();
        List<PublicCategoryDto> out = categories.findAll().stream()
                .map(c -> new PublicCategoryDto(c.id(), c.name(), c.note(), all.stream()
                        .filter(s -> s.active() && s.categoryId().equals(c.id()))
                        .map(s -> new PublicServiceDto(s.id(), s.name(), s.subcategory(), s.durationMin(),
                                s.priceCents(), new ProfessionalRef(s.professionalId(),
                                names.getOrDefault(s.professionalId(), "")), storage.publicUrl(s.photoKey())))
                        .toList()))
                .filter(c -> !c.services().isEmpty())
                .toList();
        return new ServicesResponse(out);
    }

    public AdminCatalogResponse adminCatalog(AdminPrincipal principal) {
        String own = AccessScope.professionalFilter(principal, null);
        return new AdminCatalogResponse(
                services.findAll().stream().filter(s -> own == null || s.professionalId().equals(own))
                        .map(s -> DtoMapper.toDto(s, storage)).toList(),
                categories.findAll().stream().map(DtoMapper::toDto).toList(),
                professionals.findAll().stream().filter(p -> own == null || p.id().equals(own))
                        .map(DtoMapper::toDto).toList());
    }

    /** Profissional cria sempre em nome dela; o master escolhe a profissional. */
    public ServiceDto createService(AdminPrincipal principal, Map<String, Object> body) {
        Map<String, Object> input = new HashMap<>(body == null ? Map.of() : body);
        if (!principal.isMaster()) {
            String own = principal.professionalId();
            if (input.get("professionalId") != null && !own.equals(input.get("professionalId"))) {
                throw new ForbiddenException("Você só pode criar serviços em seu nome.");
            }
            input.put("professionalId", own);
        }
        ServiceInput in = InputValidators.service(input, false);
        checkRefs(in.categoryId(), in.professionalId());
        return tx.inTransaction(() -> {
            String id = Slugs.uniqueId(in.name().orElseThrow(), services::existsById);
            SalonService created = new SalonService(id, in.name().orElseThrow(), in.categoryId().orElseThrow(),
                    in.subcategory().flatMap(Function.identity()).orElse(null), in.durationMin().orElseThrow(),
                    false, in.priceCents().flatMap(Function.identity()).orElse(null),
                    in.professionalId().orElseThrow(), in.active().orElse(true), services.maxSortOrder() + 1,
                    null, null);
            return DtoMapper.toDto(services.save(created), storage);
        });
    }

    /** Edição parcial. {@code photoUrl} no corpo é ignorado: a foto só muda pelos endpoints de foto. */
    public ServiceDto updateService(AdminPrincipal principal, String id, Map<String, Object> body) {
        SalonService current = requireOwnService(principal, id);
        ServiceInput in = InputValidators.service(body, true);
        if (!principal.isMaster() && in.professionalId().isPresent()
                && !in.professionalId().get().equals(current.professionalId())) {
            throw new ForbiddenException("Só o master pode trocar a profissional do serviço.");
        }
        checkRefs(in.categoryId(), in.professionalId());
        return tx.inTransaction(() -> {
            SalonService s = services.findById(id).orElseThrow(() -> new ResourceNotFoundException(SERVICE_NOT_FOUND));
            SalonService updated = new SalonService(s.id(), in.name().orElse(s.name()),
                    in.categoryId().orElse(s.categoryId()),
                    in.subcategory().isPresent() ? in.subcategory().get().orElse(null) : s.subcategory(),
                    in.durationMin().orElse(s.durationMin()),
                    // Duração revisada pelo salão deixa de ser estimativa
                    in.durationMin().isEmpty() && s.durationEstimated(),
                    in.priceCents().isPresent() ? in.priceCents().get().orElse(null) : s.priceCents(),
                    in.professionalId().orElse(s.professionalId()), in.active().orElse(s.active()), s.sortOrder(),
                    s.photoKey(), s.photoContentType());
            return DtoMapper.toDto(services.save(updated), storage);
        });
    }

    SalonService requireOwnService(AdminPrincipal principal, String id) {
        return services.findById(id)
                .filter(s -> AccessScope.canAccess(principal, s.professionalId()))
                .orElseThrow(() -> new ResourceNotFoundException(SERVICE_NOT_FOUND));
    }

    private void checkRefs(Optional<String> categoryId, Optional<String> professionalId) {
        Map<String, String> fields = new LinkedHashMap<>();
        if (categoryId.isPresent() && !categories.existsById(categoryId.get())) {
            fields.put("categoryId", "Categoria não encontrada.");
        }
        if (professionalId.isPresent() && !professionals.existsById(professionalId.get())) {
            fields.put("professionalId", "Profissional não encontrada.");
        }
        if (!fields.isEmpty()) {
            throw new DomainValidationException(fields);
        }
    }

    public CategoriesResponse listCategories() {
        return new CategoriesResponse(categories.findAll().stream().map(DtoMapper::toDto).toList());
    }

    public CategoryDto createCategory(AdminPrincipal principal, Map<String, Object> body) {
        AccessScope.requireMaster(principal);
        Map<String, Object> b = body == null ? Map.of() : body;
        String name = b.get("name") instanceof String s ? s.strip() : "";
        if (name.length() < 2 || name.length() > 40) {
            throw DomainValidationException.field("name", CATEGORY_NAME);
        }
        String note = b.get("note") instanceof String s && !s.isBlank() ? s.strip() : null;
        if (note != null && note.length() > 200) {
            throw DomainValidationException.field("note", CATEGORY_NOTE);
        }
        return tx.inTransaction(() -> DtoMapper.toDto(categories.save(new Category(
                Slugs.uniqueId(name, categories::existsById), name, note, categories.maxSortOrder() + 1))));
    }

    public CategoryDto updateCategory(AdminPrincipal principal, String id, Map<String, Object> body) {
        AccessScope.requireMaster(principal);
        Map<String, Object> b = body == null ? Map.of() : body;
        Map<String, String> fields = new LinkedHashMap<>();
        Optional<String> name = Optional.empty();
        if (b.containsKey("name")) {
            String n = b.get("name") instanceof String s ? s.strip() : "";
            if (n.length() < 2 || n.length() > 40) {
                fields.put("name", CATEGORY_NAME);
            } else {
                name = Optional.of(n);
            }
        }
        boolean hasNote = b.containsKey("note");
        Optional<String> note = hasNote
                ? InputValidators.optionalText(b, "note", 200, CATEGORY_NOTE, fields) : Optional.empty();
        if (!fields.isEmpty()) {
            throw new DomainValidationException(fields);
        }
        Optional<String> newName = name;
        return tx.inTransaction(() -> {
            Category c = categories.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Categoria não encontrada."));
            return DtoMapper.toDto(categories.save(new Category(c.id(), newName.orElse(c.name()),
                    hasNote ? note.orElse(null) : c.note(), c.sortOrder())));
        });
    }
}
