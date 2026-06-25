package com.ksefhelper.security.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Component
@ConditionalOnProperty(name = "app.rate-limit.store", havingValue = "memory", matchIfMissing = true)
public class InMemoryRateLimitStore implements RateLimitStore {
    private static final long CLEANUP_INTERVAL = 1024;

    private final Clock clock;
    private final ConcurrentHashMap<String, Window> windows = new ConcurrentHashMap<>();
    private final AtomicLong checks = new AtomicLong();

    public InMemoryRateLimitStore() {
        this(Clock.systemUTC());
    }

    InMemoryRateLimitStore(Clock clock) {
        this.clock = clock;
    }

    @Override
    public long consume(String key, int maximum, Duration window) {
        Instant now = clock.instant();
        Decision decision = new Decision();
        windows.compute(key, (ignored, current) -> {
            if (current == null || !now.isBefore(current.endsAt())) {
                return new Window(1, now.plus(window));
            }
            if (current.count() >= maximum) {
                decision.retryAfterSeconds = retryAfterSeconds(now, current.endsAt());
                return current;
            }
            return new Window(current.count() + 1, current.endsAt());
        });
        cleanup(now);
        return decision.retryAfterSeconds;
    }

    private void cleanup(Instant now) {
        if (checks.incrementAndGet() % CLEANUP_INTERVAL == 0) {
            windows.entrySet().removeIf(entry -> !now.isBefore(entry.getValue().endsAt()));
        }
    }

    private long retryAfterSeconds(Instant now, Instant endsAt) {
        return Math.max(1, Duration.between(now, endsAt).toSeconds() + 1);
    }

    private record Window(int count, Instant endsAt) {
    }

    private static final class Decision {
        private long retryAfterSeconds;
    }
}
