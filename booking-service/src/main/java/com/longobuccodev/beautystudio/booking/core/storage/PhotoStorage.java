package com.longobuccodev.beautystudio.booking.core.storage;

/**
 * Armazenamento das fotos dos serviços. Falha de acesso lança
 * {@link com.longobuccodev.beautystudio.booking.core.exception.StorageUnavailableException}.
 */
public interface PhotoStorage {

    void put(String key, byte[] content, String contentType);

    void delete(String key);

    /** URL pública da foto, servida pelo gateway; nula quando a chave é nula. */
    String publicUrl(String key);
}
