package com.ksefhelper.security.ratelimit;

import com.ksefhelper.common.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.UUID;

@Service
public class RateLimitService {
    private final boolean enabled;
    private final Limit loginAccountLimit;
    private final Limit loginIpLimit;
    private final Limit uploadLimit;
    private final RateLimitStore store;

    @Autowired
    public RateLimitService(
            RateLimitStore store,
            @Value("${app.rate-limit.enabled:true}") boolean enabled,
            @Value("${app.rate-limit.login.max-attempts:5}") int loginMaxAttempts,
            @Value("${app.rate-limit.login.max-attempts-per-ip:30}") int loginMaxAttemptsPerIp,
            @Value("${app.rate-limit.login.window:1m}") Duration loginWindow,
            @Value("${app.rate-limit.upload.max-requests:20}") int uploadMaxRequests,
            @Value("${app.rate-limit.upload.window:1m}") Duration uploadWindow
    ) {
        this(
                enabled,
                new Limit(loginMaxAttempts, loginWindow),
                new Limit(loginMaxAttemptsPerIp, loginWindow),
                new Limit(uploadMaxRequests, uploadWindow),
                store
        );
    }

    RateLimitService(
            boolean enabled,
            Limit loginAccountLimit,
            Limit loginIpLimit,
            Limit uploadLimit,
            Clock clock
    ) {
        this(enabled, loginAccountLimit, loginIpLimit, uploadLimit, new InMemoryRateLimitStore(clock));
    }

    RateLimitService(
            boolean enabled,
            Limit loginAccountLimit,
            Limit loginIpLimit,
            Limit uploadLimit,
            RateLimitStore store
    ) {
        this.enabled = enabled;
        this.loginAccountLimit = loginAccountLimit;
        this.loginIpLimit = loginIpLimit;
        this.uploadLimit = uploadLimit;
        this.store = store;
    }

    public void checkLogin(String email, String clientAddress) {
        if (!enabled) {
            return;
        }
        consume(
                "login-ip:" + normalize(clientAddress),
                loginIpLimit,
                "Too many login attempts from this address."
        );
        consume(
                "login-account:" + normalize(email),
                loginAccountLimit,
                "Too many login attempts for this account."
        );
    }

    public void checkUpload(UUID userId, UUID organizationId) {
        if (!enabled) {
            return;
        }
        consume(
                "upload:" + userId + ":" + organizationId,
                uploadLimit,
                "Too many invoice uploads. Try again later."
        );
    }

    private void consume(String key, Limit limit, String message) {
        long retryAfterSeconds = store.consume(key, limit.maxRequests(), limit.window());
        if (retryAfterSeconds > 0) {
            throw new RateLimitExceededException(message, retryAfterSeconds);
        }
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    record Limit(int maxRequests, Duration window) {
        Limit {
            if (maxRequests < 1) {
                throw new IllegalArgumentException("Rate limit must allow at least one request.");
            }
            if (window == null || window.isZero() || window.isNegative()) {
                throw new IllegalArgumentException("Rate limit window must be positive.");
            }
        }
    }
}
