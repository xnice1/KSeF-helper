package com.ksefhelper.invoices.dto;

import com.ksefhelper.validation.entity.ValidationStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InvoiceValidationResponse(
        UUID invoiceId,
        ValidationStatus status,
        Instant createdAt,
        List<ValidationMessageResponse> messages
) {
}
