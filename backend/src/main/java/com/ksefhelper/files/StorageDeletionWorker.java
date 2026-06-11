package com.ksefhelper.files;

import com.ksefhelper.files.entity.StorageDeletionTask;
import com.ksefhelper.files.repository.StorageDeletionTaskRepository;
import com.ksefhelper.files.storage.ObjectStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class StorageDeletionWorker {
    private final StorageDeletionTaskRepository repository;
    private final ObjectStorage objectStorage;
    private final Duration retryDelay;

    public StorageDeletionWorker(
            StorageDeletionTaskRepository repository,
            ObjectStorage objectStorage,
            @Value("${app.storage.cleanup.retry-delay}") Duration retryDelay
    ) {
        this.repository = repository;
        this.objectStorage = objectStorage;
        this.retryDelay = retryDelay;
    }

    @Scheduled(fixedDelayString = "${STORAGE_CLEANUP_POLL_INTERVAL:60000}")
    @Transactional
    public void processPending() {
        repository.findTop100ByCompletedAtIsNullAndNextAttemptAtBeforeOrderByCreatedAtAsc(Instant.now())
                .forEach(task -> process(task.getId()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void process(UUID taskId) {
        StorageDeletionTask task = repository.findById(taskId).orElse(null);
        if (task == null || task.getCompletedAt() != null || task.getNextAttemptAt().isAfter(Instant.now())) {
            return;
        }
        try {
            objectStorage.delete(task.getStorageKey());
            task.setCompletedAt(Instant.now());
            task.setLastError(null);
        } catch (RuntimeException ex) {
            task.setAttempts(task.getAttempts() + 1);
            task.setNextAttemptAt(Instant.now().plus(retryDelay.multipliedBy(Math.min(task.getAttempts(), 12))));
            task.setLastError(truncate(ex.getMessage()));
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
