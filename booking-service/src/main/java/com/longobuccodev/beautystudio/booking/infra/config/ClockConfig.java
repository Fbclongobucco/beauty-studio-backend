package com.longobuccodev.beautystudio.booking.infra.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Relógio em UTC; o fuso do salão é aplicado pelo domínio (Schedule), independente do servidor. */
@Configuration
public class ClockConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
