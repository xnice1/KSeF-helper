package com.ksefhelper.validation.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ParsedInvoice(
        String invoiceNumber,
        LocalDate issueDate,
        LocalDate saleDate,
        String sellerName,
        String sellerNip,
        String buyerName,
        String buyerNip,
        String currency,
        BigDecimal netAmount,
        BigDecimal vatAmount,
        BigDecimal grossAmount,
        String paymentMethod,
        String bankAccount,
        List<ParsedInvoiceItem> items
) {
    public static ParsedInvoice empty() {
        return new ParsedInvoice(
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of()
        );
    }
}
