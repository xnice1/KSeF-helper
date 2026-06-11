package com.ksefhelper.files.entity;

import com.ksefhelper.common.entity.AuditableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "storage_deletion_tasks")
@SuppressWarnings("JpaDataSourceORMInspection")
public class StorageDeletionTask extends AuditableEntity {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, length = 1000)
    private String storageKey;

    @Column(nullable = false)
    private int attempts;

    @Column(nullable = false)
    private Instant nextAttemptAt;

    private Instant completedAt;

    @Column(length = 1000)
    @SuppressWarnings("unused")
    private String lastError;

    public UUID getId() {
        return id;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public void setStorageKey(String storageKey) {
        this.storageKey = storageKey;
    }

    public int getAttempts() {
        return attempts;
    }

    public void setAttempts(int attempts) {
        this.attempts = attempts;
    }

    public Instant getNextAttemptAt() {
        return nextAttemptAt;
    }

    public void setNextAttemptAt(Instant nextAttemptAt) {
        this.nextAttemptAt = nextAttemptAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }
}
