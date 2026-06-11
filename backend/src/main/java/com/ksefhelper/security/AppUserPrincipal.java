package com.ksefhelper.security;

import com.ksefhelper.users.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class AppUserPrincipal implements UserDetails {
    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final UUID organizationId;
    private final boolean enabled;
    private final boolean emailVerified;
    private final boolean platformAdmin;
    private final long tokenVersion;

    public AppUserPrincipal(User user) {
        this(
                user.getId(),
                user.getEmail(),
                user.getPasswordHash(),
                null,
                user.isEnabled(),
                user.isEmailVerified(),
                user.isPlatformAdmin(),
                user.getTokenVersion()
        );
    }

    private AppUserPrincipal(
            UUID id,
            String email,
            String passwordHash,
            UUID organizationId,
            boolean enabled,
            boolean emailVerified,
            boolean platformAdmin,
            long tokenVersion
    ) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.organizationId = organizationId;
        this.enabled = enabled;
        this.emailVerified = emailVerified;
        this.platformAdmin = platformAdmin;
        this.tokenVersion = tokenVersion;
    }

    public UUID id() {
        return id;
    }

    public UUID organizationId() {
        return organizationId;
    }

    public AppUserPrincipal withOrganizationId(UUID activeOrganizationId) {
        return new AppUserPrincipal(
                id,
                email,
                passwordHash,
                activeOrganizationId,
                enabled,
                emailVerified,
                platformAdmin,
                tokenVersion
        );
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return platformAdmin ? List.of(new SimpleGrantedAuthority("ROLE_PLATFORM_ADMIN")) : List.of();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled && emailVerified;
    }

    public long tokenVersion() {
        return tokenVersion;
    }
}
