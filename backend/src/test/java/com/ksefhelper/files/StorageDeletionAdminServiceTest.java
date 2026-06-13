package com.ksefhelper.files;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksefhelper.audit.AuditEventService;
import com.ksefhelper.audit.AuditEventWriter;
import com.ksefhelper.files.entity.StorageDeletionTask;
import com.ksefhelper.files.repository.StorageDeletionTaskRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageDeletionAdminServiceTest {
    @Test
    void requeuesADeadLetterAndStartsProcessing() {
        StorageDeletionTaskRepository repository = mock(StorageDeletionTaskRepository.class);
        StorageDeletionWorker worker = mock(StorageDeletionWorker.class);
        AuditEventService audit = new AuditEventService(mock(AuditEventWriter.class), new ObjectMapper());
        StorageDeletionAdminService service = new StorageDeletionAdminService(repository, worker, audit);
        UUID taskId = UUID.randomUUID();
        StorageDeletionTask task = new StorageDeletionTask();
        task.setStorageKey("organization/file.xml");
        task.setAttempts(12);
        task.setLastError("access denied");
        task.setFailedAt(Instant.now());
        task.setNextAttemptAt(Instant.now());
        when(repository.findById(taskId)).thenReturn(Optional.of(task));
        when(repository.save(task)).thenReturn(task);

        service.requeue(taskId);

        assertThat(task.getAttempts()).isZero();
        assertThat(task.getFailedAt()).isNull();
        assertThat(task.getLastError()).isNull();
        verify(worker).process(taskId);
    }
}
