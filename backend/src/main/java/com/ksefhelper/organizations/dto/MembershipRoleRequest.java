package com.ksefhelper.organizations.dto;

import com.ksefhelper.organizations.entity.MembershipRole;
import jakarta.validation.constraints.NotNull;

public record MembershipRoleRequest(
        @NotNull MembershipRole role
) {
}
