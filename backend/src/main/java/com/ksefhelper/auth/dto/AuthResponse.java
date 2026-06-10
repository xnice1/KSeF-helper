package com.ksefhelper.auth.dto;

import com.ksefhelper.organizations.entity.OrganizationType;

import java.util.UUID;
import java.util.List;

public record AuthResponse(
        String token,
        UserProfile user,
        OrganizationProfile organization,
        List<OrganizationProfile> organizations
) {
    public record UserProfile(UUID id, String email, String firstName, String lastName) {
    }

    public record OrganizationProfile(UUID id, String name, OrganizationType type, String role) {
    }
}
