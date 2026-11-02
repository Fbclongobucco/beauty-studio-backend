package com.longobuccodev.beautystudio.booking.infra.config;

import com.longobuccodev.beautystudio.booking.application.port.LoginThrottle;
import com.longobuccodev.beautystudio.booking.application.port.TransactionRunner;
import com.longobuccodev.beautystudio.booking.application.usecase.AccountUseCase;
import com.longobuccodev.beautystudio.booking.application.usecase.AdminAppointmentUseCase;
import com.longobuccodev.beautystudio.booking.application.usecase.AuthUseCase;
import com.longobuccodev.beautystudio.booking.application.usecase.AvailabilityUseCase;
import com.longobuccodev.beautystudio.booking.application.usecase.BookingUseCase;
import com.longobuccodev.beautystudio.booking.application.usecase.CatalogUseCase;
import com.longobuccodev.beautystudio.booking.application.usecase.EarningsUseCase;
import com.longobuccodev.beautystudio.booking.application.usecase.ProfessionalUseCase;
import com.longobuccodev.beautystudio.booking.application.usecase.ServicePhotoUseCase;
import com.longobuccodev.beautystudio.booking.core.event.EventRecorder;
import com.longobuccodev.beautystudio.booking.core.repository.AccountRepository;
import com.longobuccodev.beautystudio.booking.core.repository.AppointmentRepository;
import com.longobuccodev.beautystudio.booking.core.repository.CategoryRepository;
import com.longobuccodev.beautystudio.booking.core.repository.ProfessionalRepository;
import com.longobuccodev.beautystudio.booking.core.repository.ServiceRepository;
import com.longobuccodev.beautystudio.booking.core.security.PasswordHasher;
import com.longobuccodev.beautystudio.booking.core.security.SessionTokenIssuer;
import com.longobuccodev.beautystudio.booking.core.storage.PhotoStorage;
import com.longobuccodev.beautystudio.booking.infra.storage.S3PhotoStorage;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.Duration;

/** Use cases são classes Java puras, ligadas aqui (padrão App-adm-obras). */
@Configuration
public class UseCaseConfig {

    @Bean
    CatalogUseCase catalogUseCase(ServiceRepository services, CategoryRepository categories,
                                  ProfessionalRepository professionals, PhotoStorage storage, TransactionRunner tx) {
        return new CatalogUseCase(services, categories, professionals, storage, tx);
    }

    @Bean
    ProfessionalUseCase professionalUseCase(ProfessionalRepository professionals, TransactionRunner tx) {
        return new ProfessionalUseCase(professionals, tx);
    }

    @Bean
    AvailabilityUseCase availabilityUseCase(ServiceRepository services, AppointmentRepository appointments,
                                            Clock clock) {
        return new AvailabilityUseCase(services, appointments, clock);
    }

    @Bean
    BookingUseCase bookingUseCase(ServiceRepository services, ProfessionalRepository professionals,
                                  AppointmentRepository appointments, EventRecorder events, TransactionRunner tx,
                                  Clock clock) {
        return new BookingUseCase(services, professionals, appointments, events, tx, clock);
    }

    @Bean
    AdminAppointmentUseCase adminAppointmentUseCase(AppointmentRepository appointments,
                                                    ProfessionalRepository professionals, EventRecorder events,
                                                    TransactionRunner tx, Clock clock) {
        return new AdminAppointmentUseCase(appointments, professionals, events, tx, clock);
    }

    @Bean
    EarningsUseCase earningsUseCase(AppointmentRepository appointments, Clock clock) {
        return new EarningsUseCase(appointments, clock);
    }

    @Bean
    AuthUseCase authUseCase(AccountRepository accounts, PasswordHasher hasher, SessionTokenIssuer tokens,
                            LoginThrottle throttle) {
        return new AuthUseCase(accounts, hasher, tokens, throttle);
    }

    @Bean
    AccountUseCase accountUseCase(AccountRepository accounts, ProfessionalRepository professionals,
                                  PasswordHasher hasher, TransactionRunner tx, BookingProperties properties) {
        return new AccountUseCase(accounts, professionals, hasher, tx, properties.admin().username());
    }

    @Bean
    ServicePhotoUseCase servicePhotoUseCase(ServiceRepository services, PhotoStorage storage, TransactionRunner tx,
                                            BookingProperties properties) {
        return new ServicePhotoUseCase(services, storage, tx, properties.storage().maxPhotoSize().toBytes());
    }

    @Bean
    PhotoStorage photoStorage(BookingProperties properties, CircuitBreakerFactory<?, ?> breakers) {
        return new S3PhotoStorage(properties, breakers);
    }

    /** Circuit breaker das integrações (MinIO): tempo limite acima do timeout de socket do cliente S3. */
    @Bean
    Customizer<Resilience4JCircuitBreakerFactory> circuitBreakerDefaults(BookingProperties properties) {
        Duration timeout = properties.storage().timeout().plusSeconds(5);
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(timeout).build())
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(10)
                        .minimumNumberOfCalls(5)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(Duration.ofSeconds(20))
                        .build())
                .build());
    }
}
