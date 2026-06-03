package com.ksefhelper.invoices.dto;

import com.ksefhelper.invoices.entity.InvoiceStatus;

import java.util.List;
import java.util.UUID;

public record UploadInvoiceResponse(
        UUID invoiceId,
        InvoiceStatus status,
        String invoiceNumber,
        List<ValidationMessageResponse> validationMessages
) {
}
