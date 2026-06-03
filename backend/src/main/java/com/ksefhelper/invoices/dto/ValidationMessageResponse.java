package com.ksefhelper.invoices.dto;

import com.ksefhelper.validation.entity.ValidationSeverity;

public record ValidationMessageResponse(
        ValidationSeverity severity,
        String code,
        String fieldPath,
        String message,
        String suggestion
) {
}
