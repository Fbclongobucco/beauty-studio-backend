package com.longobuccodev.beautystudio.booking.core.domain;

import java.text.Normalizer;
import java.util.Locale;
import java.util.function.Predicate;

/** Ids texto gerados a partir do nome, como na API de teste do front ("Box braids" → "box-braids"). */
public final class Slugs {

    private Slugs() {
    }

    public static String slugify(String text) {
        String s = Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");
        if (s.length() > 40) {
            s = s.substring(0, 40);
        }
        return s.isEmpty() ? "item" : s;
    }

    /** Slug livre: {@code base}, {@code base-2}, {@code base-3}... */
    public static String uniqueId(String text, Predicate<String> taken) {
        String base = slugify(text);
        String id = base;
        int n = 2;
        while (taken.test(id)) {
            id = base + "-" + n++;
        }
        return id;
    }
}
