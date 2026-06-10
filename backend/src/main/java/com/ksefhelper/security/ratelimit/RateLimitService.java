package com.ksefhelper.security.ratelimit;

import com.ksefhelper.common.exception.RateLimitExceededException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class RateLimitService {
    private static final long CLEANUP_INTERVAL = 1024;

    private final boolean enabled;
    private final Limit loginAccountLimit;
    private final Limit loginIpLimit;
    private final Limit uploadLimit;
    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong checks = new AtomicLong();

    @Autowired
    public RateLimitService(
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
                Clock.systemUTC()
        );
    }

    RateLimitService(
            boolean enabled,
            Limit loginAccountLimit,
            Limit loginIpLimit,
            Limit uploadLimit,
            Clock clock
    ) {
        this.enabled = enabled;
        this.loginAccountLimit = loginAccountLimit;
        this.loginIpLimit = loginIpLimit;
        this.uploadLimit = uploadLimit;
        this.clock = clock;
    }

    public void checkLogin(String email, String clientAddress) {
        if (!enabled) {
            return;
        }
        String address = normalize(clientAddress);
        consume("login-ip:" + address, loginIpLimit, "Too many login attempts from this address.");
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
        Instant now = clock.instant();
        Decision decision = new Decision();
        windows.compute(key, (ignored, current) -> {
            if (current == null || !now.isBefore(current.endsAt())) {
                decision.allowed = true;
                return new Window(1, now.plus(limit.window()));
            }
            if (current.count() >= limit.maxRequests()) {
                decision.retryAfterSeconds = retryAfterSeconds(now, current.endsAt());
                return current;
            }
            decision.allowed = true;
            return new Window(current.count() + 1, current.endsAt());
        });

        cleanup(now);
        if (!decision.allowed) {
            throw new RateLimitExceededException(message, decision.retryAfterSeconds);
        }
    }

    private void cleanup(Instant now) {
        if (checks.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().endsAt()));
        }
    }

    private long retryAfterSeconds(Instant now, Instant endsAt) {
        return Math.max(1, Duration.between(now, endsAt).toSeconds() + 1);
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

    private record Window(int count, Instant endsAt) {
    }

    private static final class Decision {
        private boolean allowed;
        private long retryAfterSeconds;
    }
}
