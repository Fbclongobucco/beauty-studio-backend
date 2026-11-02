package com.longobuccodev.beautystudio.booking.application.usecase;

import com.longobuccodev.beautystudio.booking.application.dto.Dtos.ProfessionalDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.ProfessionalsResponse;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.SessionDto;
import com.longobuccodev.beautystudio.booking.application.exception.ForbiddenException;
import com.longobuccodev.beautystudio.booking.application.exception.ResourceNotFoundException;
import com.longobuccodev.beautystudio.booking.application.exception.UnauthorizedException;
import com.longobuccodev.beautystudio.booking.application.mapper.DtoMapper;
import com.longobuccodev.beautystudio.booking.application.port.TransactionRunner;
import com.longobuccodev.beautystudio.booking.application.security.AccessScope;
import com.longobuccodev.beautystudio.booking.application.security.AdminPrincipal;
import com.longobuccodev.beautystudio.booking.core.domain.AccountRole;
import com.longobuccodev.beautystudio.booking.core.domain.PhoneE164;
import com.longobuccodev.beautystudio.booking.core.domain.Professional;
import com.longobuccodev.beautystudio.booking.core.domain.Slugs;
import com.longobuccodev.beautystudio.booking.core.exception.DomainValidationException;
import com.longobuccodev.beautystudio.booking.core.repository.ProfessionalRepository;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cadastro de profissionais (master) e dados da própria sessão. O WhatsApp da profissional é informado só por ela,
 * em {@code PATCH /api/admin/me}; o master apenas vê o número.
 */
public class ProfessionalUseCase {

    public static final String WHATSAPP_ONLY_OWNER =
            "Só a profissional altera o próprio WhatsApp, no painel dela.";
    static final String NAME_MESSAGE = "Informe o nome da profissional.";

    private final ProfessionalRepository professionals;
    private final TransactionRunner tx;

    public ProfessionalUseCase(ProfessionalRepository professionals, TransactionRunner tx) {
        this.professionals = professionals;
        this.tx = tx;
    }

    public ProfessionalsResponse list(AdminPrincipal principal) {
        String own = AccessScope.professionalFilter(principal, null);
        return new ProfessionalsResponse(professionals.findAll().stream()
                .filter(p -> own == null || p.id().equals(own)).map(DtoMapper::toDto).toList());
    }

    public ProfessionalDto create(AdminPrincipal principal, Map<String, Object> body) {
        AccessScope.requireMaster(principal);
        String name = normalizedName(body == null ? null : body.get("name"));
        if (name == null) {
            throw DomainValidationException.field("name", NAME_MESSAGE);
        }
        return tx.inTransaction(() -> DtoMapper.toDto(professionals.save(new Professional(
                Slugs.uniqueId(name.split(" ")[0], professionals::existsById), name, true, null))));
    }

    /** Edição pelo master: nome e ativo. O WhatsApp não é aceito aqui. */
    public ProfessionalDto update(AdminPrincipal principal, String id, Map<String, Object> body) {
        AccessScope.requireMaster(principal);
        Map<String, Object> b = body == null ? Map.of() : body;
        Map<String, String> fields = new LinkedHashMap<>();
        if (b.containsKey("whatsappPhone")) {
            fields.put("whatsappPhone", WHATSAPP_ONLY_OWNER);
        }
        String name = null;
        if (b.containsKey("name")) {
            name = normalizedName(b.get("name"));
            if (name == null) {
                fields.put("name", NAME_MESSAGE);
            }
        }
        if (b.containsKey("active") && !(b.get("active") instanceof Boolean)) {
            fields.put("active", "Valor inválido.");
        }
        if (!fields.isEmpty()) {
            throw new DomainValidationException(fields);
        }
        String newName = name;
        return tx.inTransaction(() -> {
            Professional p = professionals.findById(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Profissional não encontrada."));
            if (newName != null) {
                p = p.withName(newName);
            }
            if (b.get("active") instanceof Boolean active) {
                p = p.withActive(active);
            }
            return DtoMapper.toDto(professionals.save(p));
        });
    }

    /** {@code GET /api/admin/me}. */
    public SessionDto me(AdminPrincipal principal) {
        if (principal.isMaster()) {
            return new SessionDto(AccountRole.MASTER.value(), null, "", null);
        }
        Professional p = professionals.findById(principal.professionalId())
                .orElseThrow(() -> new UnauthorizedException("Faça login para continuar."));
        return new SessionDto(AccountRole.PROFESSIONAL.value(), p.id(), p.name(), p.whatsappPhone());
    }

    /**
     * {@code PATCH /api/admin/me} {@code { whatsappPhone }}: a profissional informa o próprio número.
     * O id vem só da sessão. {@code null} ou texto vazio remove o número.
     */
    public SessionDto updateMyWhatsapp(AdminPrincipal principal, Map<String, Object> body) {
        if (principal.isMaster()) {
            throw new ForbiddenException("Cada profissional informa o próprio WhatsApp no painel dela.");
        }
        Map<String, Object> b = body == null ? Map.of() : body;
        if (!b.containsKey("whatsappPhone")) {
            throw DomainValidationException.field("whatsappPhone", PhoneE164.MESSAGE);
        }
        Object raw = b.get("whatsappPhone");
        String phone;
        if (raw == null || (raw instanceof String s && s.isBlank())) {
            phone = null;
        } else {
            phone = PhoneE164.parse(raw)
                    .orElseThrow(() -> DomainValidationException.field("whatsappPhone", PhoneE164.MESSAGE))
                    .value();
        }
        tx.inTransaction(() -> {
            Professional p = professionals.findById(principal.professionalId())
                    .orElseThrow(() -> new UnauthorizedException("Faça login para continuar."));
            professionals.save(p.withWhatsappPhone(phone));
        });
        return me(principal);
    }

    private static String normalizedName(Object value) {
        String name = value instanceof String s ? s.strip().replaceAll("\\s+", " ") : "";
        return name.length() < 2 || name.length() > 60 ? null : name;
    }
}
