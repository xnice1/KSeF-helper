package com.ksefhelper.health;

import com.ksefhelper.audit.AuditRetentionService;
import com.ksefhelper.files.repository.StorageDeletionTaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.actuate.health.Status;
import org.springframework.stereotype.Component;

@Component("dataLifecycle")
public class DataLifecycleHealthIndicator implements HealthIndicator {
    private static final Status DEGRADED = new Status("DEGRADED");

    private final StorageDeletionTaskRepository deletionTaskRepository;
    private final AuditRetentionService auditRetentionService;
    private final long pendingWarningThreshold;

    public DataLifecycleHealthIndicator(
            StorageDeletionTaskRepository deletionTaskRepository,
            AuditRetentionService auditRetentionService,
            @Value("${app.storage.cleanup.pending-warning-threshold:1000}") long pendingWarningThreshold
    ) {
        this.deletionTaskRepository = deletionTaskRepository;
        this.auditRetentionService = auditRetentionService;
        this.pendingWarningThreshold = pendingWarningThreshold;
    }

    @Override
    public Health health() {
        long pending = deletionTaskRepository.countByCompletedAtIsNullAndFailedAtIsNull();
        long failed = deletionTaskRepository.countByFailedAtIsNotNull();
        var lastAuditRetentionRun = auditRetentionService.getLastCompletedAt();
        Health.Builder health = failed > 0 || pending > pendingWarningThreshold
                ? Health.status(DEGRADED)
                : Health.up();
        health
                .withDetail("pendingStorageDeletions", pending)
                .withDetail("failedStorageDeletions", failed);
        if (lastAuditRetentionRun != null) {
            health.withDetail("lastAuditRetentionRun", lastAuditRetentionRun);
        }
        return health.build();
    }
}
