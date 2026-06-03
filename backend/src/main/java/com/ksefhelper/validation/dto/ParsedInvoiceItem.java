package com.ksefhelper.validation.dto;

import java.math.BigDecimal;

public record ParsedInvoiceItem(
        String name,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal netAmount,
        BigDecimal vatRate,
        BigDecimal vatAmount,
        BigDecimal grossAmount
) {
}
