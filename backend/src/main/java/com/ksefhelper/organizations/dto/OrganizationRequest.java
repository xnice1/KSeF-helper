package com.ksefhelper.organizations.dto;

import com.ksefhelper.organizations.entity.OrganizationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record OrganizationRequest(
        @NotBlank @Size(max = 200) String name,
        @NotNull OrganizationType type
) {
}
