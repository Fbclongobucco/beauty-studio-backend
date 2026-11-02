package com.longobuccodev.beautystudio.notification.core.domain;

/** Formatação e máscara de telefone E.164 (+55...). */
public final class Phones {

    private Phones() {
    }

    /** "+5521968117976" → "+55219****7976": dados pessoais fora dos logs. */
    public static String mask(String e164) {
        if (e164 == null) {
            return null;
        }
        if (e164.length() < 10) {
            return "****";
        }
        return e164.substring(0, 6) + "****" + e164.substring(e164.length() - 4);
    }

    /** "+5521968117976" → "(21) 96811-7976", como no front. */
    public static String format(String e164) {
        String d = e164.replaceAll("\\D", "").replaceFirst("^55", "");
        if (d.length() == 11) {
            return "(" + d.substring(0, 2) + ") " + d.substring(2, 7) + "-" + d.substring(7);
        }
        if (d.length() == 10) {
            return "(" + d.substring(0, 2) + ") " + d.substring(2, 6) + "-" + d.substring(6);
        }
        return e164;
    }

    /** Número para o provedor: só dígitos, com DDI. */
    public static String digits(String e164) {
        return e164.replaceAll("\\D", "");
    }

    /** Primeiro nome, para logs. */
    public static String maskName(String name) {
        if (name == null || name.isBlank()) {
            return name;
        }
        String first = name.strip().split("\\s+")[0];
        return first.charAt(0) + "***";
    }
}
