package com.ksefhelper.files.dto;

import java.time.Instant;
import java.util.UUID;

public record StorageDeletionTaskResponse(
        UUID id,
        String storageKey,
        int attempts,
        Instant nextAttemptAt,
        String lastError,
        Instant failedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
