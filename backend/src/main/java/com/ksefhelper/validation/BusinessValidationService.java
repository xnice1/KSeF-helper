package com.ksefhelper.validation;

import com.ksefhelper.validation.dto.ParsedInvoice;
import com.ksefhelper.validation.dto.ParsedInvoiceItem;
import com.ksefhelper.validation.dto.ValidationIssue;
import com.ksefhelper.validation.entity.ValidationSeverity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class BusinessValidationService {
    private static final BigDecimal ROUNDING_TOLERANCE = new BigDecimal("0.02");
    private static final Set<String> FA3_VAT_RATES = Set.of(
            "23",
            "22",
            "8",
            "7",
            "5",
            "4",
            "3",
            "0 KR",
            "0 WDT",
            "0 EX",
            "ZW",
            "OO",
            "NP I",
            "NP II"
    );

    public List<ValidationIssue> validate(ParsedInvoice invoice) {
        List<ValidationIssue> issues = new ArrayList<>();

        required(invoice.invoiceNumber(), "INVOICE_NUMBER_MISSING", "invoiceNumber", "Invoice number is missing.", "Add the invoice number before sending this invoice to KSeF.", issues);
        required(invoice.issueDate(), "ISSUE_DATE_MISSING", "issueDate", "Issue date is missing.", "Add the invoice issue date in YYYY-MM-DD format.", issues);
        warning(invoice.saleDate(), "SALE_DATE_MISSING", "saleDate", "Sale date is missing.", "Check whether this invoice should include the sale date.", issues);
        required(invoice.sellerName(), "SELLER_NAME_MISSING", "seller.name", "Seller name is missing.", "Check the seller company data.", issues);
        required(invoice.sellerNip(), "SELLER_NIP_MISSING", "seller.nip", "Seller NIP is missing.", "Check the seller tax identification number.", issues);
        warning(invoice.buyerName(), "BUYER_NAME_MISSING", "buyer.name", "Buyer name is missing.", "Check the buyer company data.", issues);
        warning(invoice.buyerNip(), "BUYER_NIP_MISSING", "buyer.nip", "Buyer NIP is missing.", "Check the buyer company data before sending this invoice to KSeF.", issues);
        required(invoice.currency(), "CURRENCY_MISSING", "currency", "Currency is missing.", "Add the invoice currency, for example PLN.", issues);
        if (isTransfer(invoice.paymentMethod()) && isBlank(invoice.bankAccount())) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.WARNING,
                    "BANK_ACCOUNT_MISSING",
                    "payment.bankAccount",
                    "Bank account is missing for a transfer payment.",
                    "Add the bank account or check the payment method."
            ));
        }

        boolean correctionInvoice = isCorrectionInvoice(invoice);
        if (invoice.grossAmount() == null) {
            issues.add(error("GROSS_AMOUNT_INVALID", "totals.grossAmount", "Gross amount must be greater than zero.", "Check invoice totals."));
        }
        if (!correctionInvoice && invoice.grossAmount() != null && invoice.grossAmount().compareTo(BigDecimal.ZERO) <= 0) {
            issues.add(error("GROSS_AMOUNT_INVALID", "totals.grossAmount", "Gross amount must be greater than zero.", "Check invoice totals."));
        }
        if (!correctionInvoice && invoice.netAmount() != null && invoice.netAmount().compareTo(BigDecimal.ZERO) < 0) {
            issues.add(error("NET_AMOUNT_NEGATIVE", "totals.netAmount", "Net amount cannot be negative.", "Check invoice totals."));
        }
        if (!correctionInvoice && invoice.vatAmount() != null && invoice.vatAmount().compareTo(BigDecimal.ZERO) < 0) {
            issues.add(error("VAT_AMOUNT_NEGATIVE", "totals.vatAmount", "VAT amount cannot be negative.", "Check invoice totals."));
        }
        if (invoice.netAmount() != null && invoice.vatAmount() != null && invoice.grossAmount() != null) {
            BigDecimal expectedGross = invoice.netAmount().add(invoice.vatAmount()).setScale(2, RoundingMode.HALF_UP);
            BigDecimal actualGross = invoice.grossAmount().setScale(2, RoundingMode.HALF_UP);
            if (expectedGross.subtract(actualGross).abs().compareTo(ROUNDING_TOLERANCE) > 0) {
                issues.add(error(
                        "TOTALS_DO_NOT_MATCH",
                        "totals.grossAmount",
                        "Gross amount does not match net amount plus VAT.",
                        "Check that gross amount equals net amount plus VAT with normal currency rounding."
                ));
            }
        }
        validateItems(invoice, issues);
        return issues;
    }

    private void validateItems(ParsedInvoice invoice, List<ValidationIssue> issues) {
        List<ParsedInvoiceItem> items = invoice.items() == null ? List.of() : invoice.items();
        if (items.isEmpty()) {
            issues.add(new ValidationIssue(
                    ValidationSeverity.WARNING,
                    "ITEMS_MISSING",
                    "items",
                    "No invoice items were found.",
                    "Check whether the XML contains invoice line items."
            ));
            return;
        }

        BigDecimal itemGrossTotal = BigDecimal.ZERO;
        boolean hasItemGrossAmount = false;
        for (int i = 0; i < items.size(); i++) {
            ParsedInvoiceItem item = items.get(i);
            String prefix = "items[" + i + "]";
            warning(item.name(), "ITEM_NAME_MISSING", prefix + ".name", "An invoice item name is missing.", "Add a readable item description.", issues);
            warning(item.quantity(), "ITEM_QUANTITY_MISSING", prefix + ".quantity", "An invoice item quantity is missing.", "Check line item quantity.", issues);
            warning(item.unitPrice(), "ITEM_UNIT_PRICE_MISSING", prefix + ".unitPrice", "An invoice item unit price is missing.", "Check line item unit price.", issues);
            warning(item.netAmount(), "ITEM_NET_AMOUNT_MISSING", prefix + ".netAmount", "An invoice item net amount is missing.", "Check line item net amount.", issues);
            warning(item.vatRate(), "ITEM_VAT_RATE_MISSING", prefix + ".vatRate", "An invoice item VAT rate is missing.", "Check line item VAT rate.", issues);
            if (item.vatRate() != null && !isCommonVatRate(item.vatRate())) {
                issues.add(new ValidationIssue(
                        ValidationSeverity.WARNING,
                        "ITEM_VAT_RATE_UNUSUAL",
                        prefix + ".vatRate",
                        "An invoice item has an unusual VAT rate.",
                        "Check that the VAT rate is correct for this item."
                ));
            }
            if (item.grossAmount() != null) {
                itemGrossTotal = itemGrossTotal.add(item.grossAmount());
                hasItemGrossAmount = true;
            }
        }

        if (invoice.grossAmount() != null && hasItemGrossAmount) {
            BigDecimal expected = itemGrossTotal.setScale(2, RoundingMode.HALF_UP);
            BigDecimal actual = invoice.grossAmount().setScale(2, RoundingMode.HALF_UP);
            if (expected.subtract(actual).abs().compareTo(ROUNDING_TOLERANCE) > 0) {
                issues.add(error(
                        "ITEM_TOTAL_DOES_NOT_MATCH",
                        "items",
                        "Invoice total does not match the sum of invoice items.",
                        "Check all line item amounts and invoice totals."
                ));
            }
        }
    }

    private void required(Object value, String code, String field, String message, String suggestion, List<ValidationIssue> issues) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            issues.add(error(code, field, message, suggestion));
        }
    }

    private void warning(Object value, String code, String field, String message, String suggestion, List<ValidationIssue> issues) {
        if (value == null || (value instanceof String text && text.isBlank())) {
            issues.add(new ValidationIssue(ValidationSeverity.WARNING, code, field, message, suggestion));
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private boolean isTransfer(String paymentMethod) {
        if (paymentMethod == null) {
            return false;
        }
        String normalized = paymentMethod.trim().toLowerCase();
        return normalized.contains("transfer")
                || normalized.contains("przelew")
                || normalized.contains("bank")
                || normalized.contains("wire");
    }

    private boolean isCommonVatRate(String value) {
        return FA3_VAT_RATES.contains(value.trim().toUpperCase(Locale.ROOT));
    }

    private boolean isCorrectionInvoice(ParsedInvoice invoice) {
        String invoiceType = invoice.invoiceType();
        return invoiceType != null && invoiceType.toUpperCase(Locale.ROOT).contains("KOR");
    }

    private ValidationIssue error(String code, String field, String message, String suggestion) {
        return new ValidationIssue(ValidationSeverity.ERROR, code, field, message, suggestion);
    }
}
