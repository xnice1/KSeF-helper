package com.ksefhelper.invoices.dto;

import com.ksefhelper.invoices.entity.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceSummaryResponse(
        UUID id,
        UUID companyId,
        String invoiceNumber,
        String sellerName,
        String sellerNip,
        String buyerName,
        String buyerNip,
        LocalDate issueDate,
        LocalDate saleDate,
        String currency,
        BigDecimal netAmount,
        BigDecimal vatAmount,
        BigDecimal grossAmount,
        InvoiceStatus status,
        Instant createdAt
) {
}
