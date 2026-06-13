package com.ksefhelper.files;

import com.ksefhelper.files.entity.StorageDeletionTask;
import com.ksefhelper.files.repository.StorageDeletionTaskRepository;
import com.ksefhelper.files.storage.ObjectStorage;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StorageDeletionTaskProcessorTest {
    @Test
    void movesRepeatedStorageFailuresToADeadLetterState() {
        StorageDeletionTaskRepository repository = mock(StorageDeletionTaskRepository.class);
        ObjectStorage objectStorage = mock(ObjectStorage.class);
        StorageDeletionTaskProcessor processor = new StorageDeletionTaskProcessor(
                repository,
                objectStorage,
                Duration.ofMinutes(5),
                2
        );
        UUID taskId = UUID.randomUUID();
        StorageDeletionTask task = new StorageDeletionTask();
        task.setStorageKey("organization/file.xml");
        task.setAttempts(1);
        task.setNextAttemptAt(Instant.now());
        task.setClaimedBy("worker-1");
        task.setClaimedAt(Instant.now());
        when(repository.findById(taskId)).thenReturn(Optional.of(task));
        doThrow(new IllegalStateException("storage unavailable"))
                .when(objectStorage)
                .delete("organization/file.xml");

        processor.process(taskId, "worker-1");

        assertThat(task.getAttempts()).isEqualTo(2);
        assertThat(task.getFailedAt()).isNotNull();
        assertThat(task.getClaimedAt()).isNull();
        assertThat(task.getClaimedBy()).isNull();
        assertThat(task.getLastError()).contains("storage unavailable");
        verify(repository).save(task);
    }
}
