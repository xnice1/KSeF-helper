package com.ksefhelper.audit.dto;

import com.ksefhelper.audit.AuditEventType;

import java.time.Instant;
import java.util.UUID;

public record AuditEventResponse(
        UUID id,
        Instant occurredAt,
        UUID actorUserId,
        String actorEmail,
        UUID organizationId,
        AuditEventType eventType,
        String targetType,
        String targetId,
        String ipAddress,
        String userAgent,
        String metadata
) {
}
