package com.ksefhelper.files;

import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class StorageDeletionWorker {
    private static final Logger log = LoggerFactory.getLogger(StorageDeletionWorker.class);

    private final StorageDeletionTaskClaimer claimer;
    private final StorageDeletionTaskProcessor processor;
    private final Duration claimTimeout;
    private final int batchSize;
    private final String workerId = UUID.randomUUID().toString();

    public StorageDeletionWorker(
            StorageDeletionTaskClaimer claimer,
            StorageDeletionTaskProcessor processor,
            @Value("${app.storage.cleanup.claim-timeout:10m}") Duration claimTimeout,
            @Value("${app.storage.cleanup.batch-size:100}") int batchSize
    ) {
        this.claimer = claimer;
        this.processor = processor;
        this.claimTimeout = claimTimeout;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${STORAGE_CLEANUP_POLL_INTERVAL:60000}")
    public void processPending() {
        Instant now = Instant.now();
        List<UUID> taskIds = claimer.claimBatch(workerId, now, now.minus(claimTimeout), batchSize);
        taskIds.forEach(this::processClaimed);
    }

    public void process(UUID taskId) {
        Instant now = Instant.now();
        if (claimer.claim(taskId, workerId, now, now.minus(claimTimeout))) {
            processClaimed(taskId);
        }
    }

    private void processClaimed(UUID taskId) {
        try {
            processor.process(taskId, workerId);
        } catch (RuntimeException ex) {
            log.error("Storage deletion task processing failed taskId={} workerId={}", taskId, workerId, ex);
        }
    }
}
