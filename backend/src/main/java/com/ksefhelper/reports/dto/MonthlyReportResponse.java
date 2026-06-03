package com.ksefhelper.reports.dto;

import com.ksefhelper.invoices.entity.InvoiceStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record MonthlyReportResponse(
        int year,
        int month,
        Map<InvoiceStatus, Long> statusCounts,
        List<CurrencyTotal> currencyTotals,
        Instant generatedAt
) {
    public record CurrencyTotal(
            String currency,
            long invoiceCount,
            BigDecimal netAmount,
            BigDecimal vatAmount,
            BigDecimal grossAmount
    ) {
    }
}
