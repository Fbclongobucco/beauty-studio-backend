package com.longobuccodev.beautystudio.booking.core.domain;

/** Categoria do catálogo, com observação exibida à cliente (ex.: "Valores somente de mão de obra."). */
public record Category(String id, String name, String note, int sortOrder) {
}
