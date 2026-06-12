package com.ksefhelper.audit.entity;

import com.ksefhelper.audit.AuditEventType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

@Entity
@Immutable
@Table(name = "audit_events")
@SuppressWarnings("JpaDataSourceORMInspection")
public class AuditEvent {
    @Id
    @GeneratedValue
    private UUID id;

    @Column(nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(updatable = false)
    private UUID actorUserId;

    @Column(updatable = false)
    private String actorEmail;

    @Column(updatable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AuditEventType eventType;

    @Column(updatable = false)
    private String targetType;

    @Column(updatable = false)
    private String targetId;

    @Column(updatable = false, length = 64)
    private String ipAddress;

    @Column(updatable = false, length = 500)
    private String userAgent;

    @Column(nullable = false, updatable = false, columnDefinition = "text")
    private String metadata;

    public UUID getId() {
        return id;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public UUID getActorUserId() {
        return actorUserId;
    }

    public String getActorEmail() {
        return actorEmail;
    }

    public UUID getOrganizationId() {
        return organizationId;
    }

    public AuditEventType getEventType() {
        return eventType;
    }

    public String getTargetType() {
        return targetType;
    }

    public String getTargetId() {
        return targetId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public String getMetadata() {
        return metadata;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public void setActorUserId(UUID actorUserId) {
        this.actorUserId = actorUserId;
    }

    public void setActorEmail(String actorEmail) {
        this.actorEmail = actorEmail;
    }

    public void setOrganizationId(UUID organizationId) {
        this.organizationId = organizationId;
    }

    public void setEventType(AuditEventType eventType) {
        this.eventType = eventType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public void setUserAgent(String userAgent) {
        this.userAgent = userAgent;
    }

    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
}
