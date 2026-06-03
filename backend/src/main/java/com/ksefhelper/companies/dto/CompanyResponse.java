package com.ksefhelper.companies.dto;

import java.time.Instant;
import java.util.UUID;

public record CompanyResponse(
        UUID id,
        UUID organizationId,
        String name,
        String nip,
        String regon,
        String street,
        String city,
        String postalCode,
        String country,
        Instant createdAt,
        Instant updatedAt
) {
}
