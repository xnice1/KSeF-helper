package com.ksefhelper.auth;

import com.ksefhelper.audit.AuditEventService;
import com.ksefhelper.audit.AuditEventType;
import com.ksefhelper.auth.entity.AuthSession;
import com.ksefhelper.auth.repository.AuthSessionRepository;
import com.ksefhelper.common.exception.BadRequestException;
import com.ksefhelper.organizations.entity.Organization;
import com.ksefhelper.users.entity.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.Map;

@Service
public class RefreshSessionService {
    private final AuthSessionRepository repository;
    private final SecureTokenService secureTokenService;
    private final AuditEventService auditEventService;
    private final Duration refreshExpiration;

    public RefreshSessionService(
            AuthSessionRepository repository,
            SecureTokenService secureTokenService,
            AuditEventService auditEventService,
            @Value("${app.auth.refresh-expiration}") Duration refreshExpiration
    ) {
        this.repository = repository;
        this.secureTokenService = secureTokenService;
        this.auditEventService = auditEventService;
        this.refreshExpiration = refreshExpiration;
    }

    @Transactional
    public IssuedRefreshToken create(User user, Organization activeOrganization) {
        return create(user, activeOrganization, UUID.randomUUID());
    }

    @Transactional(noRollbackFor = BadRequestException.class)
    public RotatedRefreshToken rotate(String rawToken) {
        Instant now = Instant.now();
        AuthSession current = repository.findByTokenHashForUpdate(secureTokenService.hash(requireToken(rawToken)))
                .orElseThrow(() -> new BadRequestException("Refresh session is invalid."));

        if (current.getRevokedAt() != null) {
            repository.revokeFamily(current.getFamilyId(), now);
            auditEventService.recordForUser(
                    AuditEventType.REFRESH_TOKEN_REUSE_DETECTED,
                    current.getUser(),
                    current.getActiveOrganization() == null ? null : current.getActiveOrganization().getId(),
                    "session_family",
                    current.getFamilyId(),
                    Map.of()
            );
            throw new BadRequestException("Refresh session reuse was detected. Sign in again.");
        }
        if (!current.getExpiresAt().isAfter(now) || !current.getUser().isEnabled()) {
            current.setRevokedAt(now);
            throw new BadRequestException("Refresh session has expired.");
        }

        IssuedRefreshToken replacement = create(
                current.getUser(),
                current.getActiveOrganization(),
                current.getFamilyId()
        );
        current.setRevokedAt(now);
        current.setReplacedBy(replacement.session());
        repository.save(current);
        return new RotatedRefreshToken(
                current.getUser(),
                current.getActiveOrganization(),
                replacement.rawToken(),
                replacement.expiresAt()
        );
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHashForUpdate(secureTokenService.hash(rawToken))
                .filter(session -> session.getRevokedAt() == null)
                .ifPresent(session -> session.setRevokedAt(Instant.now()));
    }

    @Transactional
    public void updateOrganization(String rawToken, Organization organization) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        repository.findByTokenHashForUpdate(secureTokenService.hash(rawToken))
                .filter(session -> session.getRevokedAt() == null && session.getExpiresAt().isAfter(Instant.now()))
                .ifPresent(session -> session.setActiveOrganization(organization));
    }

    @Transactional
    public void revokeAll(User user) {
        repository.revokeAllForUser(user.getId(), Instant.now());
    }

    @Transactional
    public void clearActiveOrganization(User user, Organization organization) {
        repository.clearActiveOrganization(user.getId(), organization.getId());
    }

    private IssuedRefreshToken create(User user, Organization activeOrganization, UUID familyId) {
        String rawToken = secureTokenService.generate();
        Instant expiresAt = Instant.now().plus(refreshExpiration);
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setFamilyId(familyId);
        session.setTokenHash(secureTokenService.hash(rawToken));
        session.setActiveOrganization(activeOrganization);
        session.setExpiresAt(expiresAt);
        return new IssuedRefreshToken(rawToken, expiresAt, repository.save(session));
    }

    private String requireToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new BadRequestException("Refresh session is required.");
        }
        return rawToken;
    }

    public record IssuedRefreshToken(String rawToken, Instant expiresAt, AuthSession session) {
    }

    public record RotatedRefreshToken(
            User user,
            Organization activeOrganization,
            String rawToken,
            Instant expiresAt
    ) {
    }
}
