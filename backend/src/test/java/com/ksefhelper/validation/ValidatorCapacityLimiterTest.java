package com.ksefhelper.validation;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ValidatorCapacityLimiterTest {
    @Test
    void rejectsWorkWhenEveryValidatorSlotIsOccupied() throws Exception {
        ValidatorCapacityLimiter limiter = new ValidatorCapacityLimiter(1, Duration.ofMillis(20));

        try (ValidatorCapacityLimiter.Lease ignored = limiter.acquire()) {
            assertThat(limiter.active()).isEqualTo(1);
            assertThatThrownBy(limiter::acquire)
                    .isInstanceOf(IOException.class)
                    .hasMessageContaining("capacity");
        }

        assertThat(limiter.active()).isZero();
        assertThat(limiter.maximum()).isEqualTo(1);
    }
}
