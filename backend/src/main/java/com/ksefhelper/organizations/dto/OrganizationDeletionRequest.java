package com.ksefhelper.organizations.dto;

import jakarta.validation.constraints.NotBlank;

public record OrganizationDeletionRequest(
        @NotBlank String password,
        @NotBlank String confirmation
) {
}
