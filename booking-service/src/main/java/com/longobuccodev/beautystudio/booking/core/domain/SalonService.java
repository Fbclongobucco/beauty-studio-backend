package com.longobuccodev.beautystudio.booking.core.domain;

/**
 * Serviço do catálogo.
 *
 * @param priceCents        nulo = "Consultar valor"
 * @param durationEstimated duração ESTIMATIVA ainda não revisada pelo salão; some ao editar a duração
 * @param photoKey          chave do objeto da foto no armazenamento; nula = sem foto
 */
public record SalonService(String id, String name, String categoryId, String subcategory, int durationMin,
                           boolean durationEstimated, Integer priceCents, String professionalId, boolean active,
                           int sortOrder, String photoKey, String photoContentType) {

    public SalonService withPhoto(String key, String contentType) {
        return new SalonService(id, name, categoryId, subcategory, durationMin, durationEstimated, priceCents,
                professionalId, active, sortOrder, key, contentType);
    }
}
