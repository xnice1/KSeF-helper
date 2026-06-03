package com.ksefhelper.validation.dto;

import com.ksefhelper.validation.entity.ValidationSeverity;

public record ValidationIssue(
        ValidationSeverity severity,
        String code,
        String fieldPath,
        String message,
        String suggestion
) {
}
