package com.ksefhelper.health;

import com.ksefhelper.validation.Fa3ValidationRunner;
import com.ksefhelper.validation.ValidatorCapacityLimiter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

@Component("validator")
public class ValidatorHealthIndicator implements HealthIndicator {
    private static final Duration CACHE_DURATION = Duration.ofSeconds(30);

    private final Fa3ValidationRunner validationRunner;
    private final ValidatorCapacityLimiter capacityLimiter;
    private final Clock clock;
    private volatile Instant checkedAt = Instant.EPOCH;
    private volatile Health cached = Health.unknown().build();

    @Autowired
    public ValidatorHealthIndicator(
            Fa3ValidationRunner validationRunner,
            ValidatorCapacityLimiter capacityLimiter
    ) {
        this(validationRunner, capacityLimiter, Clock.systemUTC());
    }

    ValidatorHealthIndicator(
            Fa3ValidationRunner validationRunner,
            ValidatorCapacityLimiter capacityLimiter,
            Clock clock
    ) {
        this.validationRunner = validationRunner;
        this.capacityLimiter = capacityLimiter;
        this.clock = clock;
    }

    @Override
    public synchronized Health health() {
        Instant now = clock.instant();
        if (now.isBefore(checkedAt.plus(CACHE_DURATION))) {
            return cached;
        }
        if (Status.UP.equals(cached.getStatus())
                && capacityLimiter.active() >= capacityLimiter.maximum()) {
            return Health.up().withDetail("busy", true).build();
        }
        try {
            cached = validationRunner.healthCheck()
                    ? Health.up().build()
                    : Health.down().withDetail("reason", "validator self-check failed").build();
        } catch (Exception ex) {
            cached = Health.down(ex).build();
        }
        checkedAt = now;
        return cached;
    }
}
