package com.ksefhelper.audit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Service
public class AuditRetentionService {
    private final JdbcTemplate applicationJdbcTemplate;
    private final JdbcTemplate maintenanceJdbcTemplate;
    private final int personalDataDays;
    private final int retentionDays;

    public AuditRetentionService(
            JdbcTemplate applicationJdbcTemplate,
            @Value("${app.audit.maintenance.url}") String maintenanceUrl,
            @Value("${app.audit.maintenance.username}") String maintenanceUsername,
            @Value("${app.audit.maintenance.password}") String maintenancePassword,
            @Value("${app.audit.personal-data-days:90}") int personalDataDays,
            @Value("${app.audit.retention-days:365}") int retentionDays
    ) {
        this.applicationJdbcTemplate = applicationJdbcTemplate;
        this.maintenanceJdbcTemplate = new JdbcTemplate(new DriverManagerDataSource(
                maintenanceUrl,
                maintenanceUsername,
                maintenancePassword
        ));
        this.personalDataDays = personalDataDays;
        this.retentionDays = retentionDays;
    }

    @Scheduled(fixedDelayString = "${app.audit.cleanup.poll-interval:24h}")
    public void process() {
        Instant now = Instant.now();
        maintenanceJdbcTemplate.queryForObject(
                "SELECT redacted_count + deleted_count FROM run_audit_retention(?, ?)",
                Long.class,
                Timestamp.from(now.minus(personalDataDays, ChronoUnit.DAYS)),
                Timestamp.from(now.minus(retentionDays, ChronoUnit.DAYS))
        );
    }

    public Instant getLastCompletedAt() {
        return applicationJdbcTemplate.queryForObject(
                "SELECT last_completed_at FROM audit_maintenance_state WHERE id = 1",
                Instant.class
        );
    }
}
