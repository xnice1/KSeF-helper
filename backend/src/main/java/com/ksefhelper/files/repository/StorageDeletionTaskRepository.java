package com.ksefhelper.files.repository;

import com.ksefhelper.files.entity.StorageDeletionTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StorageDeletionTaskRepository extends JpaRepository<StorageDeletionTask, UUID> {
    List<StorageDeletionTask> findTop100ByCompletedAtIsNullAndNextAttemptAtBeforeOrderByCreatedAtAsc(Instant now);
}
