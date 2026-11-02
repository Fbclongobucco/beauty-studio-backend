package com.longobuccodev.beautystudio.booking.application.usecase;

import com.longobuccodev.beautystudio.booking.application.dto.Dtos.ServiceDto;
import com.longobuccodev.beautystudio.booking.application.exception.ResourceNotFoundException;
import com.longobuccodev.beautystudio.booking.application.mapper.DtoMapper;
import com.longobuccodev.beautystudio.booking.application.port.TransactionRunner;
import com.longobuccodev.beautystudio.booking.application.security.AccessScope;
import com.longobuccodev.beautystudio.booking.application.security.AdminPrincipal;
import com.longobuccodev.beautystudio.booking.core.domain.PhotoType;
import com.longobuccodev.beautystudio.booking.core.domain.SalonService;
import com.longobuccodev.beautystudio.booking.core.exception.DomainValidationException;
import com.longobuccodev.beautystudio.booking.core.exception.StorageUnavailableException;
import com.longobuccodev.beautystudio.booking.core.repository.ServiceRepository;
import com.longobuccodev.beautystudio.booking.core.storage.PhotoStorage;

import java.util.UUID;

/**
 * Foto do serviço (spec service-photo-storage, contrato da mudança add-service-photo do front): uma foto opcional,
 * trocada por {@code PUT} e removida por {@code DELETE}, com o mesmo escopo da edição do serviço.
 */
public class ServicePhotoUseCase {

    private final ServiceRepository services;
    private final PhotoStorage storage;
    private final TransactionRunner tx;
    private final long maxBytes;

    public ServicePhotoUseCase(ServiceRepository services, PhotoStorage storage, TransactionRunner tx, long maxBytes) {
        this.services = services;
        this.storage = storage;
        this.tx = tx;
        this.maxBytes = maxBytes;
    }

    public long maxBytes() {
        return maxBytes;
    }

    /** Confere sessão e escopo antes de ler o corpo: serviço alheio responde 404. */
    public void requireAccess(AdminPrincipal principal, String serviceId) {
        own(principal, serviceId);
    }

    /** {@code content} nulo = campo {@code photo} ausente. A URL nova é sempre diferente da anterior. */
    public ServiceDto setPhoto(AdminPrincipal principal, String serviceId, byte[] content) {
        SalonService current = own(principal, serviceId);
        if (content == null) {
            throw DomainValidationException.field("photo", PhotoType.MISSING_MESSAGE);
        }
        if (content.length > maxBytes) {
            throw DomainValidationException.field("photo", PhotoType.sizeMessage(maxBytes));
        }
        PhotoType type = PhotoType.detect(content)
                .orElseThrow(() -> DomainValidationException.field("photo", PhotoType.TYPE_MESSAGE));

        String key = "services/" + current.id() + "/" + UUID.randomUUID() + "." + type.extension();
        storage.put(key, content, type.contentType());
        SalonService saved;
        try {
            saved = tx.inTransaction(() -> services.save(services.findById(serviceId).orElseThrow()
                    .withPhoto(key, type.contentType())));
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }
        deleteQuietly(current.photoKey());
        return DtoMapper.toDto(saved, storage);
    }

    /** Remove o objeto e depois a referência; com o armazenamento fora do ar, nada muda (503). */
    public ServiceDto removePhoto(AdminPrincipal principal, String serviceId) {
        SalonService current = own(principal, serviceId);
        if (current.photoKey() == null) {
            return DtoMapper.toDto(current, storage);
        }
        storage.delete(current.photoKey());
        SalonService saved = tx.inTransaction(() -> services.save(services.findById(serviceId).orElseThrow()
                .withPhoto(null, null)));
        return DtoMapper.toDto(saved, storage);
    }

    private SalonService own(AdminPrincipal principal, String serviceId) {
        return services.findById(serviceId)
                .filter(s -> AccessScope.canAccess(principal, s.professionalId()))
                .orElseThrow(() -> new ResourceNotFoundException(CatalogUseCase.SERVICE_NOT_FOUND));
    }

    private void deleteQuietly(String key) {
        if (key == null) {
            return;
        }
        try {
            storage.delete(key);
        } catch (StorageUnavailableException ignored) {
            // Objeto órfão no armazenamento: não afeta o catálogo, que só aponta para a chave gravada
        }
    }
}
