-- noinspection SqlDialectInspectionForFile,SqlNoDataSourceInspection,SqlResolve

ALTER TABLE storage_deletion_tasks
    ADD COLUMN claimed_at TIMESTAMPTZ,
    ADD COLUMN claimed_by VARCHAR(100),
    ADD COLUMN failed_at TIMESTAMPTZ;

DROP INDEX idx_storage_deletion_tasks_pending;

CREATE INDEX idx_storage_deletion_tasks_pending
    ON storage_deletion_tasks(next_attempt_at, created_at)
    WHERE completed_at IS NULL AND failed_at IS NULL;

CREATE INDEX idx_storage_deletion_tasks_claimed
    ON storage_deletion_tasks(claimed_at)
    WHERE completed_at IS NULL AND failed_at IS NULL AND claimed_at IS NOT NULL;
