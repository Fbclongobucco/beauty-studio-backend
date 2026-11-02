package com.longobuccodev.beautystudio.booking.application.usecase;

import com.longobuccodev.beautystudio.booking.application.exception.LoginDisabledException;
import com.longobuccodev.beautystudio.booking.application.exception.TooManyAttemptsException;
import com.longobuccodev.beautystudio.booking.application.exception.UnauthorizedException;
import com.longobuccodev.beautystudio.booking.application.port.LoginThrottle;
import com.longobuccodev.beautystudio.booking.application.security.AdminPrincipal;
import com.longobuccodev.beautystudio.booking.core.domain.Account;
import com.longobuccodev.beautystudio.booking.core.repository.AccountRepository;
import com.longobuccodev.beautystudio.booking.core.security.PasswordHasher;
import com.longobuccodev.beautystudio.booking.core.security.SessionTokenIssuer;

import java.util.Optional;
import java.util.UUID;

/** Login por usuário e senha e validação da sessão (spec admin-auth). */
public class AuthUseCase {

    public static final String WRONG_CREDENTIALS = "Usuário ou senha incorretos";

    private final AccountRepository accounts;
    private final PasswordHasher hasher;
    private final SessionTokenIssuer tokens;
    private final LoginThrottle throttle;
    /** Hash de uma senha aleatória: usuário inexistente também paga o custo do hash (sem pista pelo tempo). */
    private final String dummyHash;

    public AuthUseCase(AccountRepository accounts, PasswordHasher hasher, SessionTokenIssuer tokens,
                       LoginThrottle throttle) {
        this.accounts = accounts;
        this.hasher = hasher;
        this.tokens = tokens;
        this.throttle = throttle;
        this.dummyHash = hasher.hash(UUID.randomUUID().toString());
    }

    /** Retorna a credencial da sessão. Usuário inexistente e senha errada geram a mesma mensagem. */
    public String login(String username, String password, String origin) {
        if (!accounts.existsAny()) {
            throw new LoginDisabledException(
                    "Login desabilitado: configure ADMIN_PASSWORD ou ADMIN_USERS no servidor.");
        }
        String user = Account.normalizeUsername(username);
        if (throttle.isBlocked(user, origin)) {
            throw new TooManyAttemptsException("Muitas tentativas de login. Aguarde alguns minutos e tente de novo.");
        }
        Optional<Account> account = accounts.findByUsername(user);
        String raw = password == null ? "" : password;
        boolean ok;
        if (account.isPresent()) {
            ok = hasher.matches(raw, account.get().passwordHash());
        } else {
            hasher.matches(raw, dummyHash);
            ok = false;
        }
        if (!ok) {
            throttle.recordFailure(user, origin);
            throw new UnauthorizedException(WRONG_CREDENTIALS);
        }
        throttle.reset(user, origin);
        return tokens.issue(account.get());
    }

    /** Sessão válida: assinatura, validade e versão da senha iguais às da conta. */
    public Optional<AdminPrincipal> authenticate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return tokens.verify(token).flatMap(claims -> accounts.findById(claims.accountId())
                .filter(a -> a.passwordVersion() == claims.passwordVersion())
                .map(a -> new AdminPrincipal(a.id(), a.username(), a.role(), a.professionalId())));
    }
}
