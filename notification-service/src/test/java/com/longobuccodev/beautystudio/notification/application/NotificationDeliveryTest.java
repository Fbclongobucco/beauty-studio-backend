package com.longobuccodev.beautystudio.notification.application;

import com.longobuccodev.beautystudio.messaging.application.TransientHandlingException;
import com.longobuccodev.beautystudio.notification.application.usecase.NotificationDelivery;
import com.longobuccodev.beautystudio.notification.core.domain.Notification;
import com.longobuccodev.beautystudio.notification.core.domain.NotificationStatus;
import com.longobuccodev.beautystudio.notification.core.domain.RecipientRole;
import com.longobuccodev.beautystudio.notification.core.exception.PermanentSendException;
import com.longobuccodev.beautystudio.notification.core.exception.TransientSendException;
import com.longobuccodev.beautystudio.notification.core.gateway.WhatsAppSender;
import com.longobuccodev.beautystudio.notification.core.repository.NotificationRepository;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 11.3: idempotência, SKIPPED, erro permanente e temporário, com fakes das portas. */
class NotificationDeliveryTest {

    static class FakeRepository implements NotificationRepository {
        final Map<String, Notification> rows = new HashMap<>();

        String key(UUID e, RecipientRole r) {
            return e + ":" + r;
        }

        public Optional<Notification> find(UUID eventId, RecipientRole role) {
            return Optional.ofNullable(rows.get(key(eventId, role)));
        }

        public Optional<Notification> insertIfAbsent(Notification n) {
            return rows.putIfAbsent(key(n.eventId(), n.recipientRole()), n) == null ? Optional.of(n) : Optional.empty();
        }

        public Notification update(Notification n) {
            rows.put(key(n.eventId(), n.recipientRole()), n);
            return n;
        }
    }

    static class FakeSender implements WhatsAppSender {
        final List<String> sent = new ArrayList<>();
        Supplier<RuntimeException> failure;

        public SendResult send(String phone, String text) {
            if (failure != null) {
                throw failure.get();
            }
            sent.add(phone);
            return new SendResult("msg-" + sent.size(), false);
        }
    }

    final FakeRepository repo = new FakeRepository();
    final FakeSender sender = new FakeSender();
    final NotificationDelivery delivery = new NotificationDelivery(repo, sender,
            Clock.fixed(Instant.parse("2026-11-02T11:00:00Z"), ZoneOffset.UTC), 3);
    final UUID eventId = UUID.randomUUID();

    NotificationDelivery.Outcome deliver(String phone) {
        return delivery.deliver(eventId, "appointment.created", RecipientRole.PROFESSIONAL, phone, "texto",
                "Profissional sem WhatsApp cadastrado no painel");
    }

    @Test
    void eventoDuplicadoGeraUmaUnicaMensagem() {
        assertThat(deliver("+5521999990000").status()).isEqualTo(NotificationStatus.SENT);
        NotificationDelivery.Outcome again = deliver("+5521999990000");
        assertThat(again.duplicate()).isTrue();
        assertThat(sender.sent).hasSize(1);
        Notification n = repo.find(eventId, RecipientRole.PROFESSIONAL).orElseThrow();
        assertThat(n.providerMessageId()).isEqualTo("msg-1");
        assertThat(n.attempts()).isEqualTo(1);
    }

    @Test
    void semWhatsappFicaSkippedSemRetentar() {
        NotificationDelivery.Outcome out = deliver(null);
        assertThat(out.status()).isEqualTo(NotificationStatus.SKIPPED);
        assertThat(repo.find(eventId, RecipientRole.PROFESSIONAL).orElseThrow().lastError())
                .isEqualTo("Profissional sem WhatsApp cadastrado no painel");
        assertThat(sender.sent).isEmpty();
    }

    @Test
    void erroPermanenteFicaFailedSemRetentar() {
        sender.failure = () -> new PermanentSendException("Número inválido ou sem WhatsApp");
        assertThat(deliver("+5521999990000").status()).isEqualTo(NotificationStatus.FAILED);
        sender.failure = null;
        assertThat(deliver("+5521999990000").duplicate()).isTrue();
        assertThat(sender.sent).isEmpty();
    }

    @Test
    void erroTemporarioRetentaEDepoisEnvia() {
        sender.failure = () -> new TransientSendException("fora do ar", null);
        assertThatThrownBy(() -> deliver("+5521999990000")).isInstanceOf(TransientHandlingException.class);
        Notification pending = repo.find(eventId, RecipientRole.PROFESSIONAL).orElseThrow();
        assertThat(pending.status()).isEqualTo(NotificationStatus.PENDING);
        assertThat(pending.attempts()).isEqualTo(1);
        sender.failure = null;
        assertThat(deliver("+5521999990000").status()).isEqualTo(NotificationStatus.SENT);
        assertThat(repo.find(eventId, RecipientRole.PROFESSIONAL).orElseThrow().attempts()).isEqualTo(2);
    }

    @Test
    void tentativasEsgotadasFicamFailed() {
        sender.failure = () -> new TransientSendException("fora do ar", null);
        for (int i = 0; i < 3; i++) {
            assertThatThrownBy(() -> deliver("+5521999990000")).isInstanceOf(TransientHandlingException.class);
        }
        Notification n = repo.find(eventId, RecipientRole.PROFESSIONAL).orElseThrow();
        assertThat(n.status()).isEqualTo(NotificationStatus.FAILED);
        assertThat(n.attempts()).isEqualTo(3);
        assertThat(n.lastError()).startsWith("Tentativas esgotadas");
    }
}
