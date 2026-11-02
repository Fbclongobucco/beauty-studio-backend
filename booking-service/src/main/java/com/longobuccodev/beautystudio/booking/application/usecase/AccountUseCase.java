package com.longobuccodev.beautystudio.booking.application.usecase;

import com.longobuccodev.beautystudio.booking.application.dto.Dtos.AccountDto;
import com.longobuccodev.beautystudio.booking.application.dto.Dtos.AccountsResponse;
import com.longobuccodev.beautystudio.booking.application.port.TransactionRunner;
import com.longobuccodev.beautystudio.booking.application.security.AccessScope;
import com.longobuccodev.beautystudio.booking.application.security.AdminPrincipal;
import com.longobuccodev.beautystudio.booking.core.domain.Account;
import com.longobuccodev.beautystudio.booking.core.domain.AccountRole;
import com.longobuccodev.beautystudio.booking.core.domain.LoginSource;
import com.longobuccodev.beautystudio.booking.core.domain.Professional;
import com.longobuccodev.beautystudio.booking.core.exception.DomainValidationException;
import com.longobuccodev.beautystudio.booking.core.repository.AccountRepository;
import com.longobuccodev.beautystudio.booking.core.repository.ProfessionalRepository;
import com.longobuccodev.beautystudio.booking.core.security.PasswordHasher;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contas do painel: sincronização com o ambiente na subida e logins criados pelo master, que persistem.
 * Conta definida no ambiente prevalece e não pode ser alterada pelo painel.
 */
public class AccountUseCase {

    public static final String DEFAULT_MASTER = "master";
    static final int MIN_PASSWORD = 6;

    private final AccountRepository accounts;
    private final ProfessionalRepository professionals;
    private final PasswordHasher hasher;
    private final TransactionRunner tx;
    private final String masterUsername;

    public AccountUseCase(AccountRepository accounts, ProfessionalRepository professionals, PasswordHasher hasher,
                          TransactionRunner tx, String masterUsername) {
        this.accounts = accounts;
        this.professionals = professionals;
        this.hasher = hasher;
        this.tx = tx;
        String u = Account.normalizeUsername(masterUsername);
        this.masterUsername = u.isEmpty() ? DEFAULT_MASTER : u;
    }

    public String masterUsername() {
        return masterUsername;
    }

    public AccountsResponse list(AdminPrincipal principal) {
        AccessScope.requireMaster(principal);
        return new AccountsResponse(masterUsername, professionals.findAll().stream()
                .map(p -> new AccountDto(p.id(), p.name(), accounts.findByProfessionalId(p.id())
                        .map(a -> a.source().value()).orElse(null)))
                .toList());
    }

    /** Cria ou troca o login de uma profissional (usuário = id dela). Trocar a senha derruba as sessões dela. */
    public AccountDto setProfessionalPassword(AdminPrincipal principal, Map<String, Object> body) {
        AccessScope.requireMaster(principal);
        Map<String, Object> b = body == null ? Map.of() : body;
        String id = b.get("professionalId") instanceof String s ? s : "";
        String password = b.get("password") instanceof String s ? s : "";
        Professional prof = professionals.findById(id).orElseThrow(() ->
                DomainValidationException.field("professionalId", "Escolha uma profissional cadastrada."));
        if (prof.id().equals(masterUsername)) {
            throw DomainValidationException.field("professionalId",
                    "Esse usuário é o mesmo do master. Altere ADMIN_USERNAME no servidor.");
        }
        if (accounts.findByProfessionalId(prof.id()).filter(a -> a.source() == LoginSource.ENV).isPresent()) {
            throw DomainValidationException.field("professionalId",
                    "A senha dessa profissional é definida no servidor (ADMIN_USERS).");
        }
        if (password.length() < MIN_PASSWORD || password.length() > 100) {
            throw DomainValidationException.field("password",
                    "A senha deve ter pelo menos " + MIN_PASSWORD + " caracteres.");
        }
        tx.inTransaction(() -> {
            String hash = hasher.hash(password);
            accounts.save(accounts.findByProfessionalId(prof.id())
                    .map(a -> a.withPassword(hash, LoginSource.DB))
                    .orElseGet(() -> new Account(UUID.randomUUID(), prof.id(), AccountRole.PROFESSIONAL, prof.id(),
                            hash, 1, LoginSource.DB)));
        });
        return new AccountDto(prof.id(), prof.name(), LoginSource.DB.value());
    }

    /** Resultado da sincronização: ids de ADMIN_USERS ignorados por não existir profissional. */
    public record SyncReport(List<String> ignoredIds, boolean masterEnabled) {
    }

    /**
     * Sincroniza as contas do ambiente: master ({@code ADMIN_USERNAME}/{@code ADMIN_PASSWORD}) e profissionais
     * ({@code ADMIN_USERS} = {@code id:senha;id:senha}). Senha diferente do hash atual incrementa a versão da senha.
     * Sem {@code ADMIN_PASSWORD}, o master fica desabilitado. Conta de ambiente que saiu da lista é removida.
     */
    public SyncReport syncFromEnvironment(String masterPassword, String adminUsers) {
        return tx.inTransaction(() -> {
            boolean masterEnabled = masterPassword != null && !masterPassword.isEmpty();
            var master = accounts.findByRole(AccountRole.MASTER);
            if (!masterEnabled) {
                master.ifPresent(accounts::delete);
            } else if (master.isEmpty()) {
                accounts.save(new Account(UUID.randomUUID(), masterUsername, AccountRole.MASTER, null,
                        hasher.hash(masterPassword), 1, LoginSource.ENV));
            } else {
                Account m = master.get();
                if (!m.username().equals(masterUsername)) {
                    m = m.withUsername(masterUsername);
                }
                if (!hasher.matches(masterPassword, m.passwordHash())) {
                    m = m.withPassword(hasher.hash(masterPassword), LoginSource.ENV);
                }
                accounts.save(m);
            }

            Map<String, String> users = parseUsers(adminUsers);
            List<String> ignored = new ArrayList<>();
            for (Map.Entry<String, String> e : users.entrySet()) {
                String id = e.getKey();
                if (!professionals.existsById(id)) {
                    ignored.add(id);
                    continue;
                }
                var existing = accounts.findByProfessionalId(id);
                if (existing.isEmpty()) {
                    accounts.save(new Account(UUID.randomUUID(), id, AccountRole.PROFESSIONAL, id,
                            hasher.hash(e.getValue()), 1, LoginSource.ENV));
                } else if (existing.get().source() != LoginSource.ENV
                        || !hasher.matches(e.getValue(), existing.get().passwordHash())) {
                    accounts.save(existing.get().withPassword(hasher.hash(e.getValue()), LoginSource.ENV));
                }
            }
            for (Account a : accounts.findAll()) {
                if (a.role() == AccountRole.PROFESSIONAL && a.source() == LoginSource.ENV
                        && !users.containsKey(a.professionalId())) {
                    accounts.delete(a);
                }
            }
            return new SyncReport(ignored, masterEnabled);
        });
    }

    /** Cada entrada é dividida no primeiro ":"; a senha pode conter ":", mas não ";". Malformadas são ignoradas. */
    Map<String, String> parseUsers(String raw) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String entry : (raw == null ? "" : raw).split(";")) {
            int i = entry.indexOf(':');
            if (i < 0) {
                continue;
            }
            String user = Account.normalizeUsername(entry.substring(0, i));
            String pw = entry.substring(i + 1);
            if (user.isEmpty() || pw.isEmpty() || user.equals(DEFAULT_MASTER) || user.equals(masterUsername)) {
                continue;
            }
            out.put(user, pw);
        }
        return out;
    }
}
