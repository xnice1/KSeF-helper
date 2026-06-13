package com.ksefhelper.files;

import com.ksefhelper.audit.AuditEventService;
import com.ksefhelper.audit.AuditEventType;
import com.ksefhelper.common.exception.BadRequestException;
import com.ksefhelper.common.exception.NotFoundException;
import com.ksefhelper.files.dto.StorageDeletionTaskResponse;
import com.ksefhelper.files.entity.StorageDeletionTask;
import com.ksefhelper.files.repository.StorageDeletionTaskRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class StorageDeletionAdminService {
    private final StorageDeletionTaskRepository repository;
    private final StorageDeletionWorker worker;
    private final AuditEventService auditEventService;

    public StorageDeletionAdminService(
            StorageDeletionTaskRepository repository,
            StorageDeletionWorker worker,
            AuditEventService auditEventService
    ) {
        this.repository = repository;
        this.worker = worker;
        this.auditEventService = auditEventService;
    }

    @Transactional(readOnly = true)
    public List<StorageDeletionTaskResponse> failedTasks() {
        return repository.findTop100ByFailedAtIsNotNullOrderByFailedAtAsc().stream()
                .map(this::response)
                .toList();
    }

    @Transactional
    public StorageDeletionTaskResponse requeue(UUID taskId) {
        StorageDeletionTask task = repository.findById(taskId)
                .orElseThrow(() -> new NotFoundException("Storage deletion task was not found."));
        if (task.getCompletedAt() != null) {
            throw new BadRequestException("Completed storage deletion tasks cannot be requeued.");
        }
        if (task.getFailedAt() == null) {
            throw new BadRequestException("Only failed storage deletion tasks can be requeued.");
        }

        int previousAttempts = task.getAttempts();
        String previousError = task.getLastError();
        task.setAttempts(0);
        task.setFailedAt(null);
        task.setLastError(null);
        task.setClaimedAt(null);
        task.setClaimedBy(null);
        task.setNextAttemptAt(Instant.now());
        StorageDeletionTask saved = repository.save(task);
        auditEventService.record(
                AuditEventType.STORAGE_DELETION_REQUEUED,
                null,
                "storage_deletion_task",
                taskId,
                Map.of(
                        "storageKey", saved.getStorageKey(),
                        "previousAttempts", previousAttempts,
                        "previousError", previousError == null ? "" : previousError
                )
        );
        afterCommit(() -> worker.process(taskId));
        return response(saved);
    }

    private StorageDeletionTaskResponse response(StorageDeletionTask task) {
        return new StorageDeletionTaskResponse(
                task.getId(),
                task.getStorageKey(),
                task.getAttempts(),
                task.getNextAttemptAt(),
                task.getLastError(),
                task.getFailedAt(),
                task.getCreatedAt(),
                task.getUpdatedAt()
        );
    }

    private void afterCommit(Runnable operation) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            operation.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                operation.run();
            }
        });
    }
}
