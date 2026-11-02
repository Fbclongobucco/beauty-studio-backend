package com.longobuccodev.beautystudio.booking.core.domain;

import java.util.Optional;

/** Formatos de foto aceitos, detectados pela assinatura do conteúdo (como beauty-studio/lib/domain/photo.ts). */
public enum PhotoType {
    JPEG("image/jpeg", "jpg"), PNG("image/png", "png"), WEBP("image/webp", "webp");

    public static final String TYPE_MESSAGE = "Envie uma foto em JPEG, PNG ou WebP.";
    public static final String MISSING_MESSAGE = "Escolha uma foto.";

    private final String contentType;
    private final String extension;

    PhotoType(String contentType, String extension) {
        this.contentType = contentType;
        this.extension = extension;
    }

    public String contentType() {
        return contentType;
    }

    public String extension() {
        return extension;
    }

    public static String sizeMessage(long maxBytes) {
        return "A foto deve ter no máximo " + (maxBytes / (1024 * 1024)) + " MB.";
    }

    /** Formato pela assinatura; vazio para qualquer outro conteúdo, independente do nome do arquivo. */
    public static Optional<PhotoType> detect(byte[] b) {
        if (startsWith(b, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(b, 0, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        // "RIFF" + 4 bytes de tamanho + "WEBP"
        if (startsWith(b, 0, 0x52, 0x49, 0x46, 0x46) && startsWith(b, 8, 0x57, 0x45, 0x42, 0x50)) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] b, int offset, int... signature) {
        if (b == null || b.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((b[offset + i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
