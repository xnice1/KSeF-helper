package com.ksefhelper.invoices.dto;

import com.ksefhelper.invoices.entity.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record InvoicePreviewResponse(
        UUID id,
        InvoiceHeader header,
        Party seller,
        Party buyer,
        Payment payment,
        Totals totals,
        List<InvoiceItemResponse> items,
        ValidationSummary validation,
        Instant uploadedAt
) {
    public record InvoiceHeader(String invoiceNumber, LocalDate issueDate, LocalDate saleDate, String currency) {
    }

    public record Party(String name, String nip) {
    }

    public record Payment(String paymentMethod, String bankAccount) {
    }

    public record Totals(BigDecimal netAmount, BigDecimal vatAmount, BigDecimal grossAmount) {
    }

    public record ValidationSummary(InvoiceStatus status, long errors, long warnings, List<ValidationMessageResponse> messages) {
    }
}
