package com.ksefhelper.companies.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CompanyRequest(
        @NotBlank @Size(max = 200) String name,
        @NotBlank @Size(max = 32) String nip,
        @Size(max = 32) String regon,
        @NotBlank @Size(max = 200) String street,
        @NotBlank @Size(max = 120) String city,
        @NotBlank @Size(max = 20) String postalCode,
        @Pattern(regexp = "^[A-Z]{2}$") String country
) {
}
