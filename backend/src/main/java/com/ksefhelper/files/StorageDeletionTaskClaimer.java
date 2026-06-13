package com.ksefhelper.files;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class StorageDeletionTaskClaimer {
    private final JdbcTemplate jdbcTemplate;

    public StorageDeletionTaskClaimer(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public List<UUID> claimBatch(String workerId, Instant now, Instant staleBefore, int limit) {
        return jdbcTemplate.queryForList(
                """
                WITH candidates AS (
                    SELECT id
                    FROM storage_deletion_tasks
                    WHERE completed_at IS NULL
                      AND failed_at IS NULL
                      AND next_attempt_at <= ?
                      AND (claimed_at IS NULL OR claimed_at < ?)
                    ORDER BY created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE storage_deletion_tasks task
                SET claimed_at = ?,
                    claimed_by = ?,
                    updated_at = ?
                FROM candidates
                WHERE task.id = candidates.id
                RETURNING task.id
                """,
                UUID.class,
                Timestamp.from(now),
                Timestamp.from(staleBefore),
                limit,
                Timestamp.from(now),
                workerId,
                Timestamp.from(now)
        );
    }

    @Transactional
    public boolean claim(UUID taskId, String workerId, Instant now, Instant staleBefore) {
        return jdbcTemplate.update(
                """
                UPDATE storage_deletion_tasks
                SET claimed_at = ?,
                    claimed_by = ?,
                    updated_at = ?
                WHERE id = ?
                  AND completed_at IS NULL
                  AND failed_at IS NULL
                  AND next_attempt_at <= ?
                  AND (claimed_at IS NULL OR claimed_at < ?)
                """,
                Timestamp.from(now),
                workerId,
                Timestamp.from(now),
                taskId,
                Timestamp.from(now),
                Timestamp.from(staleBefore)
        ) == 1;
    }
}
