package com.longobuccodev.beautystudio.booking.core.domain;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class TrackedRequestsTest {

    static final UUID A = UUID.randomUUID();
    static final UUID B = UUID.randomUUID();

    @Test
    void incluiNoInicioSemDuplicar() {
        TrackedRequests t = TrackedRequests.EMPTY.withFirst(A).withFirst(B).withFirst(A);
        assertThat(t.ids()).containsExactly(A, B);
    }

    @Test
    void limiteDe10TiraOMaisAntigo() {
        List<UUID> ten = IntStream.range(0, 10).mapToObj(i -> UUID.randomUUID()).toList();
        TrackedRequests full = new TrackedRequests(ten);
        UUID novo = UUID.randomUUID();
        TrackedRequests next = full.withFirst(novo);
        assertThat(next.ids()).hasSize(10).first().isEqualTo(novo);
        assertThat(next.ids()).doesNotContain(ten.getLast()).contains(ten.getFirst());
    }

    @Test
    void mantemSoOsQueExistem() {
        TrackedRequests t = new TrackedRequests(List.of(A, B));
        assertThat(t.retainOnly(List.of(B)).ids()).containsExactly(B);
        assertThat(t.retainOnly(List.of(A, B))).isEqualTo(t);
    }
}
