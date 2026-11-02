package com.longobuccodev.beautystudio.booking.core.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Portado de beauty-studio/lib/domain/validation.test.ts. */
class ValueObjectsTest {

    @Test
    void telefoneRejeitaSemDddENormalizaParaE164() {
        assertThat(PhoneE164.parse("9681-7976")).isEmpty();
        assertThat(PhoneE164.parse("(21) 96811-7976")).contains(new PhoneE164("+5521968117976"));
        assertThat(PhoneE164.parse("+55 21 96811-7976")).contains(new PhoneE164("+5521968117976"));
        assertThat(PhoneE164.parse("(21) 2650-1234")).contains(new PhoneE164("+552126501234"));
        assertThat(PhoneE164.parse("(21) 86811-7976")).isEmpty();
        assertThat(PhoneE164.parse("(21) 99999-0000")).contains(new PhoneE164("+5521999990000"));
        assertThat(PhoneE164.parse(null)).isEmpty();
        assertThat(PhoneE164.parse(21968117976L)).isEmpty();
    }

    @Test
    void duracaoExigeMultiploDe30Entre30E480() {
        assertThat(Duration30.validate(45)).contains("múltiplo de 30");
        assertThat(Duration30.validate(0)).isNotNull();
        assertThat(Duration30.validate(510)).isNotNull();
        assertThat(Duration30.validate(90)).isNull();
        assertThat(Duration30.validate(90.0)).isNull();
        assertThat(Duration30.validate("90")).isEqualTo("Informe a duração em minutos.");
        assertThat(Duration30.validate(90.5)).isEqualTo("Informe a duração em minutos.");
    }

    @Test
    void precoEmCentavosInteirosNaoNegativosOuNulo() {
        assertThat(Money.validateCents(null)).isNull();
        assertThat(Money.validateCents(4500)).isNull();
        assertThat(Money.validateCents(-100)).isEqualTo("Informe um valor em reais, ex.: 45,00");
        assertThat(Money.validateCents("45,00")).isEqualTo(Money.MESSAGE);
        assertThat(Money.validateCents(45.5)).isEqualTo(Money.MESSAGE);
    }

    @Test
    void slugsComoNaApiDeTeste() {
        assertThat(Slugs.slugify("Box braids")).isEqualTo("box-braids");
        assertThat(Slugs.slugify("Spa dos pés")).isEqualTo("spa-dos-pes");
        assertThat(Slugs.slugify("!!!")).isEqualTo("item");
        assertThat(Slugs.uniqueId("Ana", id -> id.equals("ana") || id.equals("ana-2"))).isEqualTo("ana-3");
    }
}
