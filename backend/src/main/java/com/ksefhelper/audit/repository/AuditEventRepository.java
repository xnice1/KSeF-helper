package com.ksefhelper.audit.repository;

import com.ksefhelper.audit.entity.AuditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {
    List<AuditEvent> findTop200ByOrganizationIdOrderByOccurredAtDesc(UUID organizationId);

    List<AuditEvent> findAllByOrganizationIdOrderByOccurredAtAsc(UUID organizationId);
}
