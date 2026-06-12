package com.ksefhelper.audit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ksefhelper.audit.entity.AuditEvent;
import com.ksefhelper.security.AppUserPrincipal;
import com.ksefhelper.users.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class AuditEventService {
    private final AuditEventWriter writer;
    private final ObjectMapper objectMapper;

    public AuditEventService(AuditEventWriter writer, ObjectMapper objectMapper) {
        this.writer = writer;
        this.objectMapper = objectMapper;
    }

    public void record(
            AuditEventType eventType,
            UUID organizationId,
            String targetType,
            Object targetId,
            Map<String, ?> metadata
    ) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AppUserPrincipal principal = authentication != null && authentication.getPrincipal() instanceof AppUserPrincipal value
                ? value
                : null;
        writer.write(event(
                eventType,
                principal == null ? null : principal.id(),
                principal == null ? null : principal.getUsername(),
                organizationId,
                targetType,
                targetId,
                metadata
        ));
    }

    public void recordForUser(
            AuditEventType eventType,
            User user,
            UUID organizationId,
            String targetType,
            Object targetId,
            Map<String, ?> metadata
    ) {
        writer.write(event(
                eventType,
                user.getId(),
                user.getEmail(),
                organizationId,
                targetType,
                targetId,
                metadata
        ));
    }

    public void recordSystem(
            AuditEventType eventType,
            UUID organizationId,
            String targetType,
            Object targetId,
            Map<String, ?> metadata
    ) {
        writer.write(event(eventType, null, "system", organizationId, targetType, targetId, metadata));
    }

    private AuditEvent event(
            AuditEventType eventType,
            UUID actorUserId,
            String actorEmail,
            UUID organizationId,
            String targetType,
            Object targetId,
            Map<String, ?> metadata
    ) {
        HttpServletRequest request = currentRequest();
        AuditEvent event = new AuditEvent();
        event.setOccurredAt(Instant.now());
        event.setActorUserId(actorUserId);
        event.setActorEmail(actorEmail);
        event.setOrganizationId(organizationId);
        event.setEventType(eventType);
        event.setTargetType(targetType);
        event.setTargetId(targetId == null ? null : targetId.toString());
        event.setIpAddress(request == null ? null : truncate(request.getRemoteAddr(), 64));
        event.setUserAgent(request == null ? null : truncate(request.getHeader("User-Agent"), 500));
        event.setMetadata(json(metadata == null ? Map.of() : metadata));
        return event;
    }

    private String json(Map<String, ?> metadata) {
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Audit metadata could not be serialized.", ex);
        }
    }

    private String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }

    private HttpServletRequest currentRequest() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest();
        }
        return null;
    }
}
