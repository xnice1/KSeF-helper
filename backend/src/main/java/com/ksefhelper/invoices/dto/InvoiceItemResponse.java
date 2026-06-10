package com.ksefhelper.invoices.dto;

import java.math.BigDecimal;
import java.util.UUID;

public record InvoiceItemResponse(
        UUID id,
        String name,
        BigDecimal quantity,
        BigDecimal unitPrice,
        BigDecimal netAmount,
        String vatRate,
        BigDecimal vatAmount,
        BigDecimal grossAmount
) {
}
