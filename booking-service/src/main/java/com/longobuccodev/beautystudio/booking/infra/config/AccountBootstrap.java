package com.longobuccodev.beautystudio.booking.infra.config;

import com.longobuccodev.beautystudio.booking.application.usecase.AccountUseCase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Sincroniza as contas do ambiente (master e ADMIN_USERS) a cada subida. */
@Component
public class AccountBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AccountBootstrap.class);

    private final AccountUseCase accounts;
    private final BookingProperties properties;

    public AccountBootstrap(AccountUseCase accounts, BookingProperties properties) {
        this.accounts = accounts;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        AccountUseCase.SyncReport report = accounts.syncFromEnvironment(properties.admin().password(),
                properties.admin().users());
        report.ignoredIds().forEach(id -> log.warn(
                "ADMIN_USERS: \"{}\" nao corresponde a nenhuma profissional e foi ignorado.", id));
        if (!report.masterEnabled()) {
            log.warn("ADMIN_PASSWORD vazio: conta master desabilitada.");
        }
    }
}
