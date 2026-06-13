package com.ksefhelper.files.repository;

import com.ksefhelper.files.entity.StorageDeletionTask;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface StorageDeletionTaskRepository extends JpaRepository<StorageDeletionTask, UUID> {
    long countByCompletedAtIsNullAndFailedAtIsNull();

    long countByFailedAtIsNotNull();

    List<StorageDeletionTask> findTop100ByFailedAtIsNotNullOrderByFailedAtAsc();
}
