package com.ksefhelper.users.dto;

import jakarta.validation.constraints.NotBlank;

public record AccountDeletionRequest(
        @NotBlank String password,
        @NotBlank String confirmation
) {
}
