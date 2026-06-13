package com.ksefhelper.organizations.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OwnershipTransferRequest(
        @NotNull UUID membershipId
) {
}
