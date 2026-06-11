package com.ksefhelper.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
@Profile("prod")
public class ProductionConfigurationValidator implements ApplicationRunner {
    private static final String DEVELOPMENT_SECRET =
            "ZmFrZS1kZWZhdWx0LWtleS1mb3ItZGV2ZWxvcG1lbnQtMzItYnl0ZXM=";

    private final String jwtSecret;
    private final String allowedOrigins;
    private final String storageType;
    private final String mailDelivery;
    private final boolean secureRefreshCookie;

    public ProductionConfigurationValidator(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.cors.allowed-origins}") String allowedOrigins,
            @Value("${app.storage.type}") String storageType,
            @Value("${app.mail.delivery}") String mailDelivery,
            @Value("${app.auth.refresh-cookie-secure}") boolean secureRefreshCookie
    ) {
        this.jwtSecret = jwtSecret;
        this.allowedOrigins = allowedOrigins;
        this.storageType = storageType;
        this.mailDelivery = mailDelivery;
        this.secureRefreshCookie = secureRefreshCookie;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (DEVELOPMENT_SECRET.equals(jwtSecret) || decodedLength(jwtSecret) < 32) {
            throw new IllegalStateException("Production JWT_SECRET must be unique and at least 256 bits.");
        }
        if (allowedOrigins.contains("*") || allowedOrigins.contains("localhost")) {
            throw new IllegalStateException("Production CORS_ALLOWED_ORIGINS must contain explicit production origins.");
        }
        if (!"s3".equalsIgnoreCase(storageType)) {
            throw new IllegalStateException("Production FILE_STORAGE_TYPE must be s3.");
        }
        if (!"smtp".equalsIgnoreCase(mailDelivery)) {
            throw new IllegalStateException("Production MAIL_DELIVERY must be smtp.");
        }
        if (!secureRefreshCookie) {
            throw new IllegalStateException("Production refresh cookies must be secure.");
        }
    }

    private int decodedLength(String value) {
        try {
            return Base64.getDecoder().decode(value).length;
        } catch (IllegalArgumentException ex) {
            return 0;
        }
    }
}
