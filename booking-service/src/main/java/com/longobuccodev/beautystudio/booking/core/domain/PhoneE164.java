package com.longobuccodev.beautystudio.booking.core.domain;

import java.util.Optional;

/** Telefone brasileiro com DDD (10 ou 11 dígitos, com ou sem +55, com ou sem máscara) em E.164. */
public record PhoneE164(String value) {

    public static final String MESSAGE = "Informe o WhatsApp com DDD, ex.: (21) 96811-7976";

    public static Optional<PhoneE164> parse(Object input) {
        if (!(input instanceof String s)) {
            return Optional.empty();
        }
        String d = s.replaceAll("\\D", "");
        if ((d.length() == 12 || d.length() == 13) && d.startsWith("55")) {
            d = d.substring(2);
        }
        if (d.length() != 10 && d.length() != 11) {
            return Optional.empty();
        }
        int ddd = Integer.parseInt(d.substring(0, 2));
        if (ddd < 11 || ddd > 99) {
            return Optional.empty();
        }
        if (d.length() == 11 && d.charAt(2) != '9') {
            return Optional.empty();
        }
        return Optional.of(new PhoneE164("+55" + d));
    }

    @Override
    public String toString() {
        return value;
    }
}
