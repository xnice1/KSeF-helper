package com.ksefhelper.invoices.dto;

import com.ksefhelper.invoices.entity.InvoiceStatus;
import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record InvoiceFilterRequest(
        String invoiceNumber,
        String sellerNip,
        String buyerNip,
        UUID companyId,
        String currency,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate uploadedFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate uploadedTo,
        InvoiceStatus status,
        BigDecimal minGrossAmount,
        BigDecimal maxGrossAmount
) {
}
