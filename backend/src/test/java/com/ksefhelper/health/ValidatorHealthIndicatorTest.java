package com.ksefhelper.health;

import com.ksefhelper.validation.Fa3ValidationRunner;
import com.ksefhelper.validation.SchemaValidationResult;
import com.ksefhelper.validation.ValidatorCapacityLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.Status;

import java.io.File;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class ValidatorHealthIndicatorTest {

    @Test
    void remainsReadyWhenHealthyValidatorCapacityIsTemporarilySaturated() throws Exception {
        AtomicInteger healthChecks = new AtomicInteger();
        Fa3ValidationRunner runner = new Fa3ValidationRunner() {
            @Override
            public SchemaValidationResult validate(File xmlFile) {
                return SchemaValidationResult.validResult();
            }

            @Override
            public boolean healthCheck() {
                healthChecks.incrementAndGet();
                return true;
            }
        };
        ValidatorCapacityLimiter limiter = new ValidatorCapacityLimiter(1, Duration.ofMillis(20));
        MutableClock clock = new MutableClock(Instant.parse("2026-06-14T12:00:00Z"));
        ValidatorHealthIndicator indicator = new ValidatorHealthIndicator(runner, limiter, clock);

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        clock.advance(Duration.ofSeconds(31));

        try (ValidatorCapacityLimiter.Lease ignored = limiter.acquire()) {
            Health busy = indicator.health();
            assertThat(busy.getStatus()).isEqualTo(Status.UP);
            assertThat(busy.getDetails()).containsEntry("busy", true);
            assertThat(healthChecks).hasValue(1);
        }

        assertThat(indicator.health().getStatus()).isEqualTo(Status.UP);
        assertThat(healthChecks).hasValue(2);
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
