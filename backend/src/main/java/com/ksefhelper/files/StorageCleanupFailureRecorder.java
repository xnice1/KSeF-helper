package com.ksefhelper.files;

import com.ksefhelper.files.entity.StorageDeletionTask;
import com.ksefhelper.files.repository.StorageDeletionTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class StorageCleanupFailureRecorder {
    private final StorageDeletionTaskRepository repository;

    public StorageCleanupFailureRecorder(StorageDeletionTaskRepository repository) {
        this.repository = repository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String storageKey, RuntimeException failure) {
        StorageDeletionTask task = new StorageDeletionTask();
        task.setStorageKey(storageKey);
        task.setAttempts(1);
        task.setNextAttemptAt(Instant.now());
        task.setLastError(truncate(failure.getMessage()));
        repository.save(task);
    }

    private String truncate(String message) {
        if (message == null) {
            return "Rollback storage cleanup failed.";
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
