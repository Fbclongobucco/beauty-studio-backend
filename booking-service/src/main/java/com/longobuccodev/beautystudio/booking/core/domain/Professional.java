package com.longobuccodev.beautystudio.booking.core.domain;

/** Profissional do salão. {@code whatsappPhone} (E.164) é informado só pela própria profissional. */
public record Professional(String id, String name, boolean active, String whatsappPhone) {

    public Professional withWhatsappPhone(String phone) {
        return new Professional(id, name, active, phone);
    }

    public Professional withName(String newName) {
        return new Professional(id, newName, active, whatsappPhone);
    }

    public Professional withActive(boolean value) {
        return new Professional(id, name, value, whatsappPhone);
    }
}
