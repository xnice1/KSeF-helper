package com.ksefhelper.reports.dto;

import com.ksefhelper.invoices.dto.InvoiceSummaryResponse;
import com.ksefhelper.invoices.dto.ValidationMessageResponse;
import com.ksefhelper.validation.entity.ValidationStatus;

import java.time.Instant;
import java.util.List;

public record ValidationReportResponse(
        InvoiceSummaryResponse invoice,
        ValidationStatus validationStatus,
        List<ValidationMessageResponse> errors,
        List<ValidationMessageResponse> warnings,
        List<String> suggestions,
        Instant generatedAt
) {
}
