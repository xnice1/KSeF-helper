package com.ksefhelper.audit;

import com.ksefhelper.audit.dto.AuditEventResponse;
import com.ksefhelper.audit.entity.AuditEvent;

public final class AuditEventResponses {
    private AuditEventResponses() {
    }

    public static AuditEventResponse from(AuditEvent event) {
        return new AuditEventResponse(
                event.getId(),
                event.getOccurredAt(),
                event.getActorUserId(),
                event.getActorEmail(),
                event.getOrganizationId(),
                event.getEventType(),
                event.getTargetType(),
                event.getTargetId(),
                event.getIpAddress(),
                event.getUserAgent(),
                event.getMetadata(),
                event.getRedactedAt()
        );
    }
}
