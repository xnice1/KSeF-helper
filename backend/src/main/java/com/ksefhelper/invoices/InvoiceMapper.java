package com.ksefhelper.invoices;

import com.ksefhelper.invoices.dto.InvoiceItemResponse;
import com.ksefhelper.invoices.dto.InvoicePreviewResponse;
import com.ksefhelper.invoices.dto.InvoiceSummaryResponse;
import com.ksefhelper.invoices.dto.ValidationMessageResponse;
import com.ksefhelper.invoices.entity.Invoice;
import com.ksefhelper.invoices.entity.InvoiceItem;
import com.ksefhelper.validation.entity.ValidationMessage;
import com.ksefhelper.validation.entity.ValidationSeverity;
import com.ksefhelper.validation.entity.ValidationResult;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class InvoiceMapper {
    public InvoiceSummaryResponse toSummary(Invoice invoice) {
        return new InvoiceSummaryResponse(
                invoice.getId(),
                invoice.getCompany() == null ? null : invoice.getCompany().getId(),
                invoice.getInvoiceNumber(),
                invoice.getSellerName(),
                invoice.getSellerNip(),
                invoice.getBuyerName(),
                invoice.getBuyerNip(),
                invoice.getIssueDate(),
                invoice.getSaleDate(),
                invoice.getCurrency(),
                invoice.getNetAmount(),
                invoice.getVatAmount(),
                invoice.getGrossAmount(),
                invoice.getStatus(),
                invoice.getCreatedAt()
        );
    }

    public InvoicePreviewResponse toPreview(Invoice invoice, ValidationResult validationResult) {
        List<ValidationMessageResponse> messages = validationResult.getMessages().stream()
                .sorted(Comparator.comparing(ValidationMessage::getSeverity).thenComparing(ValidationMessage::getCode))
                .map(this::toValidationMessage)
                .toList();
        long errors = messages.stream().filter(message -> message.severity() == ValidationSeverity.ERROR).count();
        long warnings = messages.stream().filter(message -> message.severity() == ValidationSeverity.WARNING).count();

        return new InvoicePreviewResponse(
                invoice.getId(),
                new InvoicePreviewResponse.InvoiceHeader(
                        invoice.getInvoiceNumber(),
                        invoice.getIssueDate(),
                        invoice.getSaleDate(),
                        invoice.getCurrency()
                ),
                new InvoicePreviewResponse.Party(invoice.getSellerName(), invoice.getSellerNip()),
                new InvoicePreviewResponse.Party(invoice.getBuyerName(), invoice.getBuyerNip()),
                new InvoicePreviewResponse.Payment(invoice.getPaymentMethod(), invoice.getBankAccount()),
                new InvoicePreviewResponse.Totals(invoice.getNetAmount(), invoice.getVatAmount(), invoice.getGrossAmount()),
                invoice.getItems().stream().map(this::toItem).toList(),
                new InvoicePreviewResponse.ValidationSummary(invoice.getStatus(), errors, warnings, messages),
                invoice.getCreatedAt()
        );
    }

    public InvoiceItemResponse toItem(InvoiceItem item) {
        return new InvoiceItemResponse(
                item.getId(),
                item.getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.getNetAmount(),
                item.getVatRate(),
                item.getVatAmount(),
                item.getGrossAmount()
        );
    }

    public ValidationMessageResponse toValidationMessage(ValidationMessage message) {
        return new ValidationMessageResponse(
                message.getSeverity(),
                message.getCode(),
                message.getFieldPath(),
                message.getMessage(),
                message.getSuggestion()
        );
    }
}
