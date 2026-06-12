package com.ksefhelper.users;

import com.ksefhelper.audit.AuditEventService;
import com.ksefhelper.audit.AuditEventType;
import com.ksefhelper.auth.RefreshSessionService;
import com.ksefhelper.common.exception.NotFoundException;
import com.ksefhelper.users.entity.User;
import com.ksefhelper.users.repository.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class UserAdminService {
    private final UserRepository userRepository;
    private final RefreshSessionService refreshSessionService;
    private final AuditEventService auditEventService;

    public UserAdminService(
            UserRepository userRepository,
            RefreshSessionService refreshSessionService,
            AuditEventService auditEventService
    ) {
        this.userRepository = userRepository;
        this.refreshSessionService = refreshSessionService;
        this.auditEventService = auditEventService;
    }

    @Transactional
    public void setEnabled(UUID userId, boolean enabled) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User was not found."));
        user.setEnabled(enabled);
        user.setCredentialsChangedAt(Instant.now());
        user.incrementTokenVersion();
        userRepository.save(user);
        if (!enabled) {
            refreshSessionService.revokeAll(user);
        }
        auditEventService.record(
                enabled ? AuditEventType.ACCOUNT_ENABLED : AuditEventType.ACCOUNT_DISABLED,
                null,
                "user",
                user.getId(),
                Map.of("email", user.getEmail())
        );
    }
}
