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
    private final boolean permanentDeleteVersions;
    private final int auditPersonalDataDays;
    private final int auditRetentionDays;
    private final String runtimeDatabaseRole;
    private final String migrationDatabaseRole;
    private final String auditMaintenanceDatabaseRole;
    private final String rateLimitStore;
    private final String sentryDsn;

    public ProductionConfigurationValidator(
            @Value("${app.jwt.secret}") String jwtSecret,
            @Value("${app.cors.allowed-origins}") String allowedOrigins,
            @Value("${app.storage.type}") String storageType,
            @Value("${app.mail.delivery}") String mailDelivery,
            @Value("${app.auth.refresh-cookie-secure}") boolean secureRefreshCookie,
            @Value("${app.storage.s3.permanent-delete-versions:false}") boolean permanentDeleteVersions,
            @Value("${app.audit.personal-data-days}") int auditPersonalDataDays,
            @Value("${app.audit.retention-days}") int auditRetentionDays,
            @Value("${spring.datasource.username}") String runtimeDatabaseRole,
            @Value("${spring.flyway.user:}") String migrationDatabaseRole,
            @Value("${app.audit.maintenance.username}") String auditMaintenanceDatabaseRole,
            @Value("${app.rate-limit.store}") String rateLimitStore,
            @Value("${sentry.dsn:}") String sentryDsn
    ) {
        this.jwtSecret = jwtSecret;
        this.allowedOrigins = allowedOrigins;
        this.storageType = storageType;
        this.mailDelivery = mailDelivery;
        this.secureRefreshCookie = secureRefreshCookie;
        this.permanentDeleteVersions = permanentDeleteVersions;
        this.auditPersonalDataDays = auditPersonalDataDays;
        this.auditRetentionDays = auditRetentionDays;
        this.runtimeDatabaseRole = runtimeDatabaseRole;
        this.migrationDatabaseRole = migrationDatabaseRole;
        this.auditMaintenanceDatabaseRole = auditMaintenanceDatabaseRole;
        this.rateLimitStore = rateLimitStore;
        this.sentryDsn = sentryDsn;
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
        if (!permanentDeleteVersions) {
            throw new IllegalStateException("Production S3 deletion must remove every object version.");
        }
        if (auditPersonalDataDays <= 0 || auditRetentionDays < auditPersonalDataDays) {
            throw new IllegalStateException(
                    "Production audit retention must be positive and not shorter than personal-data retention."
            );
        }
        if (runtimeDatabaseRole.isBlank()
                || migrationDatabaseRole.isBlank()
                || auditMaintenanceDatabaseRole.isBlank()
                || runtimeDatabaseRole.equals(migrationDatabaseRole)
                || runtimeDatabaseRole.equals(auditMaintenanceDatabaseRole)
                || migrationDatabaseRole.equals(auditMaintenanceDatabaseRole)) {
            throw new IllegalStateException(
                    "Production must use distinct runtime, migration, and audit-maintenance database roles."
            );
        }
        if (!"redis".equalsIgnoreCase(rateLimitStore)) {
            throw new IllegalStateException("Production RATE_LIMIT_STORE must be redis.");
        }
        if (sentryDsn.isBlank()) {
            throw new IllegalStateException("Production SENTRY_DSN must be configured.");
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
