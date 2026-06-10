package com.ksefhelper.security.ratelimit;

import com.ksefhelper.common.exception.RateLimitExceededException;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RateLimitServiceTest {
    @Test
    void blocksRequestsAfterTheConfiguredLimit() {
        MutableClock clock = new MutableClock();
        RateLimitService service = service(clock);

        service.checkLogin("user@example.com", "127.0.0.1");
        service.checkLogin("user@example.com", "127.0.0.1");

        assertThatThrownBy(() -> service.checkLogin("user@example.com", "127.0.0.1"))
                .isInstanceOf(RateLimitExceededException.class)
                .satisfies(error -> assertThat(((RateLimitExceededException) error).retryAfterSeconds()).isPositive());
    }

    @Test
    void allowsRequestsAgainAfterTheWindowExpires() {
        MutableClock clock = new MutableClock();
        RateLimitService service = service(clock);
        UUID userId = UUID.randomUUID();
        UUID organizationId = UUID.randomUUID();

        service.checkUpload(userId, organizationId);
        assertThatThrownBy(() -> service.checkUpload(userId, organizationId))
                .isInstanceOf(RateLimitExceededException.class);

        clock.advance(Duration.ofSeconds(11));

        service.checkUpload(userId, organizationId);
    }

    @Test
    void keepsLoginAccountsAndUploadOrganizationsIndependent() {
        MutableClock clock = new MutableClock();
        RateLimitService service = service(clock);
        UUID userId = UUID.randomUUID();

        service.checkLogin("first@example.com", "127.0.0.1");
        service.checkLogin("first@example.com", "127.0.0.1");
        service.checkLogin("second@example.com", "127.0.0.1");
        service.checkUpload(userId, UUID.randomUUID());
        service.checkUpload(userId, UUID.randomUUID());
    }

    @Test
    void appliesTheAccountLimitAcrossDifferentClientAddresses() {
        MutableClock clock = new MutableClock();
        RateLimitService service = service(clock);

        service.checkLogin("user@example.com", "192.0.2.1");
        service.checkLogin("USER@example.com", "192.0.2.2");

        assertThatThrownBy(() -> service.checkLogin(" user@example.com ", "192.0.2.3"))
                .isInstanceOf(RateLimitExceededException.class)
                .hasMessageContaining("account");
    }

    private RateLimitService service(Clock clock) {
        return new RateLimitService(
                true,
                new RateLimitService.Limit(2, Duration.ofSeconds(10)),
                new RateLimitService.Limit(20, Duration.ofSeconds(10)),
                new RateLimitService.Limit(1, Duration.ofSeconds(10)),
                clock
        );
    }

    private static final class MutableClock extends Clock {
        private Instant instant = Instant.parse("2026-06-10T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }
    }
}
