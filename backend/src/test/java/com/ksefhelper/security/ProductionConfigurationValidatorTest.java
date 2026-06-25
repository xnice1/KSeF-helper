package com.ksefhelper.security;

import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProductionConfigurationValidatorTest {
    @Test
    void acceptsExplicitProductionConfiguration() {
        String secret = Base64.getEncoder().encodeToString(new byte[48]);
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
                secret,
                "https://app.example.com",
                "s3",
                "smtp",
                true,
                true,
                90,
                365,
                "ksef_app",
                "ksef_migrator",
                "ksef_audit_maintainer",
                "redis",
                "https://public@example.ingest.sentry.io/1"
        );

        assertThatCode(() -> validator.run(new DefaultApplicationArguments()))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsDevelopmentDefaultsInProduction() {
        ProductionConfigurationValidator validator = new ProductionConfigurationValidator(
                "ZmFrZS1kZWZhdWx0LWtleS1mb3ItZGV2ZWxvcG1lbnQtMzItYnl0ZXM=",
                "http://localhost:5173",
                "local",
                "log",
                false,
                false,
                0,
                0,
                "ksef",
                "ksef",
                "ksef",
                "memory",
                ""
        );

        assertThatThrownBy(() -> validator.run(new DefaultApplicationArguments()))
                .isInstanceOf(IllegalStateException.class);
    }
}
