package com.ksefhelper.security;

import com.ksefhelper.users.entity.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public class AppUserPrincipal implements UserDetails {
    private final UUID id;
    private final String email;
    private final String passwordHash;
    private final UUID organizationId;

    public AppUserPrincipal(User user) {
        this(user.getId(), user.getEmail(), user.getPasswordHash(), null);
    }

    private AppUserPrincipal(UUID id, String email, String passwordHash, UUID organizationId) {
        this.id = id;
        this.email = email;
        this.passwordHash = passwordHash;
        this.organizationId = organizationId;
    }

    public UUID id() {
        return id;
    }

    public UUID organizationId() {
        return organizationId;
    }

    public AppUserPrincipal withOrganizationId(UUID activeOrganizationId) {
        return new AppUserPrincipal(id, email, passwordHash, activeOrganizationId);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of();
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }
}
