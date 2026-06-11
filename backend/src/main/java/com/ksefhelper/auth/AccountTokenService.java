package com.ksefhelper.auth;

import com.ksefhelper.auth.entity.AccountToken;
import com.ksefhelper.auth.entity.AccountTokenType;
import com.ksefhelper.auth.repository.AccountTokenRepository;
import com.ksefhelper.common.exception.BadRequestException;
import com.ksefhelper.users.entity.User;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
public class AccountTokenService {
    private final AccountTokenRepository repository;
    private final SecureTokenService secureTokenService;

    public AccountTokenService(AccountTokenRepository repository, SecureTokenService secureTokenService) {
        this.repository = repository;
        this.secureTokenService = secureTokenService;
    }

    @Transactional
    public String issue(User user, AccountTokenType type, Duration lifetime) {
        Instant now = Instant.now();
        repository.invalidateActive(user.getId(), type, now);
        String rawToken = secureTokenService.generate();
        AccountToken token = new AccountToken();
        token.setUser(user);
        token.setType(type);
        token.setTokenHash(secureTokenService.hash(rawToken));
        token.setExpiresAt(now.plus(lifetime));
        repository.save(token);
        return rawToken;
    }

    @Transactional
    public User consume(String rawToken, AccountTokenType type) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("Account token is invalid or expired.");
        }
        AccountToken token = repository.findForUpdate(secureTokenService.hash(rawToken), type)
                .orElseThrow(() -> new BadRequestException("Account token is invalid or expired."));
        Instant now = Instant.now();
        if (token.getUsedAt() != null || !token.getExpiresAt().isAfter(now)) {
            throw new BadRequestException("Account token is invalid or expired.");
        }
        token.setUsedAt(now);
        return token.getUser();
    }
}
