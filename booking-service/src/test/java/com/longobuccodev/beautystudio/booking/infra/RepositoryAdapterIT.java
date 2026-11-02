package com.longobuccodev.beautystudio.booking.infra;

import com.longobuccodev.beautystudio.booking.core.domain.Appointment;
import com.longobuccodev.beautystudio.booking.core.domain.AppointmentStatus;
import com.longobuccodev.beautystudio.booking.core.domain.Professional;
import com.longobuccodev.beautystudio.booking.core.domain.StatusAction;
import com.longobuccodev.beautystudio.booking.core.exception.SlotTakenException;
import com.longobuccodev.beautystudio.booking.core.repository.AppointmentRepository;
import com.longobuccodev.beautystudio.booking.core.repository.AppointmentRepository.Filter;
import com.longobuccodev.beautystudio.booking.core.repository.ProfessionalRepository;
import com.longobuccodev.beautystudio.booking.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 6.3: adaptadores das portas do core sobre JPA. */
class RepositoryAdapterIT extends IntegrationTest {

    @Autowired
    AppointmentRepository appointments;
    @Autowired
    ProfessionalRepository professionals;

    Appointment appt(String prof, String date, String time, int duration, AppointmentStatus status) {
        return new Appointment(UUID.randomUUID(), "manicure", prof, "Manicure", prof, duration, 2000,
                LocalDate.parse(date), LocalTime.parse(time), "Maria", "+5521988887777", null, status,
                Instant.parse("2026-11-02T11:00:00Z"), null, null, null, null, null);
    }

    @Test
    void salvaEBuscaAgendamentoComTodosOsCampos() {
        Appointment a = appt("stefani", "2026-11-06", "11:00", 30, AppointmentStatus.PENDING);
        appointments.save(a);
        Appointment back = appointments.findById(a.id()).orElseThrow();
        assertThat(back).usingRecursiveComparison().isEqualTo(a);
        assertThat(back.start().toString()).isEqualTo("2026-11-06T11:00-03:00");
    }

    @Test
    void filtraPorStatusDataIntervaloEProfissionalEmOrdemDeHorario() {
        appointments.save(appt("stefani", "2026-11-06", "15:00", 30, AppointmentStatus.PENDING));
        appointments.save(appt("stefani", "2026-11-06", "09:00", 30, AppointmentStatus.CONFIRMED));
        appointments.save(appt("lorrayne", "2026-11-06", "10:00", 60, AppointmentStatus.PENDING));
        appointments.save(appt("stefani", "2026-11-09", "10:00", 30, AppointmentStatus.CANCELLED));

        assertThat(appointments.find(new Filter(null, LocalDate.parse("2026-11-06"), null, null, "stefani")))
                .extracting(a -> a.time().toString()).containsExactly("09:00", "15:00");
        assertThat(appointments.find(new Filter(AppointmentStatus.PENDING, null, null, null, null))).hasSize(2);
        assertThat(appointments.find(new Filter(null, null, LocalDate.parse("2026-11-07"),
                LocalDate.parse("2026-11-09"), null))).hasSize(1);
        assertThat(appointments.findActive(Set.of("stefani"), LocalDate.parse("2026-11-06"),
                LocalDate.parse("2026-11-09"))).hasSize(2);
        assertThat(appointments.findConfirmed("stefani", LocalDate.parse("2026-11-06"))).hasSize(1);
        assertThat(appointments.pendingCount(null)).isEqualTo(new AppointmentRepository.PendingCount(2, 2));
        assertThat(appointments.pendingCount("lorrayne")).isEqualTo(new AppointmentRepository.PendingCount(1, 1));
    }

    @Test
    void confirmarSobrepostoNoBancoViraSlotTaken() {
        appointments.save(appt("stefani", "2026-11-06", "11:00", 60, AppointmentStatus.CONFIRMED));
        Appointment other = appointments.save(appt("stefani", "2026-11-06", "11:30", 30, AppointmentStatus.PENDING));
        other.apply(StatusAction.CONFIRM, null, Instant.now(), "stefani");
        assertThatThrownBy(() -> appointments.save(other)).isInstanceOf(SlotTakenException.class);
    }

    @Test
    void profissionalMantemOrdemDeCadastroEAtualizaWhatsapp() {
        Professional p = professionals.findById("stefani").orElseThrow();
        professionals.save(p.withWhatsappPhone("+5521999990000"));
        assertThat(professionals.findById("stefani").orElseThrow().whatsappPhone()).isEqualTo("+5521999990000");
        professionals.save(new Professional("ana", "Ana Souza", true, null));
        assertThat(professionals.findAll()).extracting(Professional::id)
                .containsExactly("stefani", "lorrayne", "michele", "ana");
        assertThat(List.of(professionals.existsById("ana"), professionals.existsById("x"))).containsExactly(true, false);
    }
}
