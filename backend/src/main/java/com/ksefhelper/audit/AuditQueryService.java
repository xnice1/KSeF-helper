package com.ksefhelper.audit;

import com.ksefhelper.audit.dto.AuditEventResponse;
import com.ksefhelper.audit.repository.AuditEventRepository;
import com.ksefhelper.security.CurrentUserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AuditQueryService {
    private final AuditEventRepository repository;
    private final CurrentUserService currentUserService;

    public AuditQueryService(AuditEventRepository repository, CurrentUserService currentUserService) {
        this.repository = repository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> currentAccountEvents() {
        return repository.findTop200ByActorUserIdOrderByOccurredAtDesc(currentUserService.currentUser().getId()).stream()
                .map(AuditEventResponses::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AuditEventResponse> platformEvents() {
        return repository.findTop200ByOrderByOccurredAtDesc().stream()
                .map(AuditEventResponses::from)
                .toList();
    }
}
