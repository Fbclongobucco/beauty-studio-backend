package com.longobuccodev.beautystudio.booking.core.domain;

/** Número inteiro vindo de JSON (equivalente a {@code Number.isInteger} do front). */
final class Numbers {

    private Numbers() {
    }

    static Long asLong(Object value) {
        if (value instanceof Integer || value instanceof Long || value instanceof Short) {
            return ((Number) value).longValue();
        }
        if (value instanceof java.math.BigInteger b) {
            return b.bitLength() < 63 ? b.longValue() : null;
        }
        if (value instanceof Number n) {
            double d = n.doubleValue();
            return Double.isFinite(d) && d == Math.rint(d) && Math.abs(d) < 1e15 ? (long) d : null;
        }
        return null;
    }
}
