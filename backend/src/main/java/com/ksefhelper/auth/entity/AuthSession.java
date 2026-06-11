package com.ksefhelper.auth.entity;

import com.ksefhelper.common.entity.AuditableEntity;
import com.ksefhelper.organizations.entity.Organization;
import com.ksefhelper.users.entity.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "auth_sessions")
@SuppressWarnings("JpaDataSourceORMInspection")
public class AuthSession extends AuditableEntity {
    @Id
    @GeneratedValue
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private UUID familyId;

    @Column(nullable = false, unique = true, length = 64)
    @SuppressWarnings("unused")
    private String tokenHash;

    @ManyToOne
    @JoinColumn(name = "active_organization_id")
    private Organization activeOrganization;

    @Column(nullable = false)
    private Instant expiresAt;

    private Instant revokedAt;

    @OneToOne
    @JoinColumn(name = "replaced_by_id")
    @SuppressWarnings("unused")
    private AuthSession replacedBy;

    public UUID getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public void setFamilyId(UUID familyId) {
        this.familyId = familyId;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Organization getActiveOrganization() {
        return activeOrganization;
    }

    public void setActiveOrganization(Organization activeOrganization) {
        this.activeOrganization = activeOrganization;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Instant expiresAt) {
        this.expiresAt = expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(Instant revokedAt) {
        this.revokedAt = revokedAt;
    }

    public void setReplacedBy(AuthSession replacedBy) {
        this.replacedBy = replacedBy;
    }
}
