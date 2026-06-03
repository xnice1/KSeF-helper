package com.ksefhelper.organizations.dto;

import com.ksefhelper.organizations.entity.MembershipRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record InviteMemberRequest(
        @Email @NotBlank String email,
        @NotNull MembershipRole role
) {
}
