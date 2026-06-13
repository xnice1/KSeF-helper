package com.ksefhelper.files;

import com.ksefhelper.files.entity.StorageDeletionTask;
import com.ksefhelper.files.repository.StorageDeletionTaskRepository;
import com.ksefhelper.files.storage.ObjectStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class StorageDeletionTaskProcessor {
    private final StorageDeletionTaskRepository repository;
    private final ObjectStorage objectStorage;
    private final Duration retryDelay;
    private final int maxAttempts;

    public StorageDeletionTaskProcessor(
            StorageDeletionTaskRepository repository,
            ObjectStorage objectStorage,
            @Value("${app.storage.cleanup.retry-delay}") Duration retryDelay,
            @Value("${app.storage.cleanup.max-attempts:12}") int maxAttempts
    ) {
        this.repository = repository;
        this.objectStorage = objectStorage;
        this.retryDelay = retryDelay;
        this.maxAttempts = maxAttempts;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(UUID taskId, String workerId) {
        StorageDeletionTask task = repository.findById(taskId).orElse(null);
        if (task == null
                || task.getCompletedAt() != null
                || task.getFailedAt() != null
                || !workerId.equals(task.getClaimedBy())) {
            return;
        }

        try {
            objectStorage.delete(task.getStorageKey());
            task.setCompletedAt(Instant.now());
            task.setLastError(null);
        } catch (RuntimeException ex) {
            int attempts = task.getAttempts() + 1;
            task.setAttempts(attempts);
            task.setLastError(truncate(ex.getMessage()));
            if (attempts >= maxAttempts) {
                task.setFailedAt(Instant.now());
            } else {
                task.setNextAttemptAt(Instant.now().plus(retryDelay.multipliedBy(Math.min(attempts, 12))));
            }
        } finally {
            task.setClaimedAt(null);
            task.setClaimedBy(null);
        }
        repository.save(task);
    }

    private String truncate(String message) {
        if (message == null) {
            return "Unknown storage deletion error.";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
