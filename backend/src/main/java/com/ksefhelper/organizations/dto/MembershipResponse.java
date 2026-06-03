package com.ksefhelper.organizations.dto;

import com.ksefhelper.organizations.entity.MembershipRole;

import java.util.UUID;

public record MembershipResponse(
        UUID id,
        UUID userId,
        String email,
        String fullName,
        MembershipRole role
) {
}
