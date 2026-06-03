package com.ksefhelper.organizations.dto;

import com.ksefhelper.organizations.entity.OrganizationType;

import java.util.UUID;

public record OrganizationResponse(
        UUID id,
        String name,
        OrganizationType type
) {
}
