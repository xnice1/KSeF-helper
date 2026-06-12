package com.ksefhelper.audit;

import com.ksefhelper.audit.entity.AuditEvent;
import com.ksefhelper.audit.repository.AuditEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuditEventWriter {
    private final AuditEventRepository repository;

    public AuditEventWriter(AuditEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void write(AuditEvent event) {
        repository.save(event);
    }
}
